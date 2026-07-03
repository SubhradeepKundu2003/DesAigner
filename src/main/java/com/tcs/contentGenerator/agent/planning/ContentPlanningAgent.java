package com.tcs.contentGenerator.agent.planning;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Agent #3 of the pipeline. Takes the classified {@link ContentItem}s from
 * Content Understanding and turns them into a {@link NewsletterPlan}: one LLM
 * call scores every item's newsletter impact (1–10 with a rationale), then
 * deterministic code selects the issue — items below the score threshold are
 * deferred, each section is capped, sections follow the canonical
 * {@link NewsletterSection} order, and a Leadership Message placeholder leads
 * the issue for the generation agent to write.
 *
 * <p>If the scoring call fails, every item falls back to a neutral score so the
 * pipeline still produces a (less curated) plan rather than aborting.
 */
@Component
@Order(3)
public class ContentPlanningAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ContentPlanningAgent.class);
    private static final int FALLBACK_SCORE = 5;

    private final LlmClient llm;
    private final int minScore;
    private final int maxItemsPerSection;

    public ContentPlanningAgent(LlmClient llm,
            @Value("${app.planning.min-score:4}") int minScore,
            @Value("${app.planning.max-items-per-section:5}") int maxItemsPerSection) {
        this.llm = llm;
        this.minScore = minScore;
        this.maxItemsPerSection = maxItemsPerSection;
    }

    @Override
    public String name() {
        return "Content Planning Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        List<ContentItem> items = context.getContentItems();
        if (items.isEmpty()) {
            log.info("No content items to plan, producing empty plan");
            context.setNewsletterPlan(new NewsletterPlan(issueTitle(), List.of(), List.of()));
            return;
        }

        Map<Integer, ScoredItem> scores = scoreItems(items);
        List<PlannedItem> planned = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ScoredItem scored = scores.get(i);
            int score = scored == null ? FALLBACK_SCORE : clamp(scored.score());
            String rationale = scored == null ? "Not scored by the model; kept with a neutral score."
                    : scored.rationale();
            planned.add(new PlannedItem(items.get(i), score, rationale));
        }

        NewsletterPlan plan = assemble(planned);
        context.setNewsletterPlan(plan);
        log.info("Planned issue \"{}\": {} section(s), {} item(s) selected, {} deferred",
                plan.issueTitle(), plan.sections().size(), plan.selectedItemCount(),
                plan.deferredItems().size());
    }

    /** One scoring call for all items; on failure every item gets the fallback score. */
    private Map<Integer, ScoredItem> scoreItems(List<ContentItem> items) {
        try {
            ScoredItem[] scored = llm.generate(
                    PlanningPrompts.SYSTEM,
                    PlanningPrompts.USER_TEMPLATE.formatted(renderCandidates(items)),
                    ScoredItem[].class);
            Map<Integer, ScoredItem> byIndex = new HashMap<>();
            if (scored != null) {
                for (ScoredItem s : scored) {
                    if (s != null && s.index() >= 0 && s.index() < items.size()) {
                        byIndex.putIfAbsent(s.index(), s);
                    }
                }
            }
            log.info("Model scored {} of {} item(s)", byIndex.size(), items.size());
            return byIndex;
        } catch (Exception e) {
            log.warn("Impact scoring failed, falling back to neutral scores: {}", e.toString());
            return Map.of();
        }
    }

    private String renderCandidates(List<ContentItem> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            ContentItem item = items.get(i);
            sb.append(i).append(". [").append(item.category().label())
                    .append(" / ").append(item.type()).append("] ")
                    .append(item.title()).append('\n')
                    .append("   ").append(item.summary()).append('\n');
            if (!item.keyMetrics().isEmpty()) {
                sb.append("   Metrics: ").append(String.join("; ", item.keyMetrics())).append('\n');
            }
        }
        return sb.toString();
    }

    /** Deterministic selection + ordering: threshold, per-section cap, canonical order. */
    private NewsletterPlan assemble(List<PlannedItem> planned) {
        List<PlannedItem> deferred = new ArrayList<>();
        Map<NewsletterSection, List<PlannedItem>> bySection = new EnumMap<>(NewsletterSection.class);
        for (PlannedItem item : planned) {
            if (item.score() < minScore) {
                deferred.add(item);
            } else {
                bySection.computeIfAbsent(NewsletterSection.fromCategory(item.item().category()),
                        s -> new ArrayList<>()).add(item);
            }
        }

        List<SectionPlan> sections = new ArrayList<>();
        for (NewsletterSection section : NewsletterSection.values()) {
            List<PlannedItem> candidates = bySection.get(section);
            if (candidates == null) {
                continue;
            }
            candidates.sort(Comparator.comparingInt(PlannedItem::score).reversed());
            List<PlannedItem> kept = candidates.subList(0, Math.min(maxItemsPerSection, candidates.size()));
            deferred.addAll(candidates.subList(kept.size(), candidates.size()));
            sections.add(new SectionPlan(section, kept));
        }
        if (!sections.isEmpty()) {
            sections.add(0, new SectionPlan(NewsletterSection.LEADERSHIP_MESSAGE, List.of()));
        }
        deferred.sort(Comparator.comparingInt(PlannedItem::score).reversed());
        return new NewsletterPlan(issueTitle(), sections, deferred);
    }

    private String issueTitle() {
        String monthYear = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        return "TD Monthly Newsletter — " + monthYear;
    }

    private static int clamp(int score) {
        return Math.max(1, Math.min(10, score));
    }
}
