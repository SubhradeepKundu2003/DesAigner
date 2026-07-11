package com.tcs.contentGenerator.agent.generation;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.planning.NewsletterPlan;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.agent.planning.PlannedItem;
import com.tcs.contentGenerator.agent.planning.SectionPlan;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Agent #4 of the pipeline. Walks the {@link NewsletterPlan} and writes the
 * issue: one LLM call per planned item turns its extracted facts into a short
 * reader-friendly article (headline + body), and one call writes the Leadership
 * Message from a digest of the issue's highlights. A single system prompt
 * ({@link GenerationPrompts#SYSTEM}) carries the house tone across every call.
 *
 * <p>The model returns plain text ("first line = headline, rest = body"), not
 * JSON — long prose in JSON strings is where the small local model breaks. A
 * failed call falls back to the item's extracted title and summary, so one bad
 * generation degrades a single article instead of aborting the issue.
 */
@Component
@Order(4)
public class ContentGenerationAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ContentGenerationAgent.class);
    private static final String LEADERSHIP_FALLBACK_HEADLINE = "A Message from Leadership";
    /** Anything longer than this is body prose, not a headline. */
    private static final int MAX_HEADLINE_CHARS = 120;
    /**
     * One {@code POINT: label | text} protocol line, wherever it appears (the
     * model is asked to put them after the body but is not trusted to).
     * Tolerates bullet/markdown prefixes the small model sometimes adds.
     */
    private static final java.util.regex.Pattern POINT_LINE = java.util.regex.Pattern.compile(
            "(?im)^\\s*[-*>\\d.\\s]*point\\s*:\\s*(.+?)\\s*$");
    /** More points than any infographic can hold is the model rambling — keep the first few. */
    private static final int MAX_POINTS = 6;
    /** A "label" this long is a sentence the model failed to split — not a point. */
    private static final int MAX_POINT_LABEL_CHARS = 80;

    private final LlmClient llm;

    public ContentGenerationAgent(LlmClient llm) {
        this.llm = llm;
    }

    @Override
    public String name() {
        return "Content Generation Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        NewsletterPlan plan = context.getNewsletterPlan();
        if (plan == null || plan.sections().isEmpty()) {
            log.info("No newsletter plan to write, skipping generation");
            context.setGeneratedNewsletter(new GeneratedNewsletter(
                    plan == null ? "" : plan.issueTitle(), List.of()));
            return;
        }

        List<GeneratedSection> sections = new ArrayList<>();
        for (SectionPlan sectionPlan : plan.sections()) {
            sections.add(new GeneratedSection(sectionPlan.section(),
                    writeSection(plan, sectionPlan)));
        }

        GeneratedNewsletter newsletter = new GeneratedNewsletter(plan.issueTitle(), sections);
        context.setGeneratedNewsletter(newsletter);
        log.info("Wrote issue \"{}\": {} article(s) across {} section(s)",
                newsletter.issueTitle(), newsletter.articleCount(), sections.size());
    }

    private List<GeneratedArticle> writeSection(NewsletterPlan plan, SectionPlan sectionPlan) {
        if (sectionPlan.section() == NewsletterSection.LEADERSHIP_MESSAGE) {
            return List.of(writeLeadershipMessage(plan));
        }
        List<GeneratedArticle> articles = new ArrayList<>();
        for (PlannedItem planned : sectionPlan.items()) {
            articles.add(writeArticle(sectionPlan.section(), planned, plan.issueTitle()));
        }
        return articles;
    }

    private GeneratedArticle writeArticle(NewsletterSection section, PlannedItem planned,
            String issueTitle) {
        ContentItem item = planned.item();
        try {
            String raw = llm.generate(
                    GenerationPrompts.SYSTEM,
                    GenerationPrompts.ARTICLE_TEMPLATE.formatted(
                            section.title(), issueTitle, item.title(), item.summary(),
                            item.keyMetrics().isEmpty() ? "none"
                                    : String.join("; ", item.keyMetrics())));
            GeneratedArticle article = parse(raw, item.title(), item.summary(), planned);
            log.info("[{}] wrote article \"{}\"", section.title(), article.headline());
            return article;
        } catch (Exception e) {
            log.warn("[{}] generation failed for \"{}\", using extracted text: {}",
                    section.title(), item.title(), e.toString());
            return new GeneratedArticle(item.title(), item.summary(), planned);
        }
    }

    /** The Leadership Message has no source items — it is written from an issue digest. */
    private GeneratedArticle writeLeadershipMessage(NewsletterPlan plan) {
        try {
            String raw = llm.generate(
                    GenerationPrompts.SYSTEM,
                    GenerationPrompts.LEADERSHIP_TEMPLATE.formatted(
                            plan.issueTitle(), highlightsDigest(plan)));
            GeneratedArticle article = parse(raw, LEADERSHIP_FALLBACK_HEADLINE, "", null);
            log.info("[Leadership Message] wrote \"{}\"", article.headline());
            return article;
        } catch (Exception e) {
            log.warn("Leadership Message generation failed, leaving placeholder: {}", e.toString());
            return new GeneratedArticle(LEADERSHIP_FALLBACK_HEADLINE,
                    "This month's leadership message could not be generated automatically.",
                    null);
        }
    }

    /** One line per content section with its top story titles, for the leadership prompt. */
    private String highlightsDigest(NewsletterPlan plan) {
        StringBuilder sb = new StringBuilder();
        for (SectionPlan section : plan.sections()) {
            if (section.items().isEmpty()) {
                continue;
            }
            sb.append("- ").append(section.section().title()).append(": ");
            sb.append(String.join("; ", section.items().stream()
                    .limit(3)
                    .map(p -> p.item().title())
                    .toList()));
            sb.append('\n');
        }
        return sb.isEmpty() ? "- (no highlights this month)" : sb.toString();
    }

    /**
     * Parses the plain-text protocol: first non-blank line = headline, the rest =
     * body, with optional {@code POINT: label | text} lines pulled out first
     * (wherever they sit) into {@link GeneratedArticle#points()}. Tolerates the
     * model's habits (markdown fences, "Headline:" prefixes, decoration) and
     * falls back to the extracted title/summary when a part is missing, so this
     * never throws.
     */
    private GeneratedArticle parse(String raw, String fallbackHeadline, String fallbackBody,
            PlannedItem source) {
        String text = raw == null ? "" : raw.strip();
        text = text.replaceAll("(?m)^```[a-zA-Z]*\\s*$", "").strip();
        List<GeneratedArticle.Point> points = extractPoints(text);
        text = POINT_LINE.matcher(text).replaceAll("").strip();

        String headline = "";
        String body = "";
        int firstBreak = text.indexOf('\n');
        if (firstBreak < 0) {
            headline = text;
        } else {
            headline = text.substring(0, firstBreak);
            body = text.substring(firstBreak + 1);
        }

        headline = cleanHeadline(headline);
        body = body.strip().replaceAll("\\n{3,}", "\n\n");

        // Observed live: the model sometimes skips the headline and opens with
        // prose. A paragraph-length "headline" is body text — reclaim it and let
        // the fallback title take over.
        if (headline.length() > MAX_HEADLINE_CHARS) {
            body = body.isBlank() ? headline : headline + "\n\n" + body;
            headline = "";
        }

        if (headline.isBlank()) {
            headline = fallbackHeadline;
        }
        if (body.isBlank()) {
            body = fallbackBody;
        }
        return new GeneratedArticle(headline, body, source, points);
    }

    /**
     * Every well-formed {@code POINT: label | text} line, capped at
     * {@link #MAX_POINTS}. Malformed lines (no pipe and too long to be a bare
     * label, or an empty label) are dropped rather than guessed at — a missing
     * point only costs an infographic candidate, never content: the body prose
     * still carries the facts.
     */
    private static List<GeneratedArticle.Point> extractPoints(String text) {
        List<GeneratedArticle.Point> points = new ArrayList<>();
        java.util.regex.Matcher matcher = POINT_LINE.matcher(text);
        while (matcher.find() && points.size() < MAX_POINTS) {
            String line = matcher.group(1).strip();
            String label;
            String pointText;
            int pipe = line.indexOf('|');
            if (pipe >= 0) {
                label = line.substring(0, pipe).strip();
                pointText = line.substring(pipe + 1).strip();
            } else {
                label = line;
                pointText = "";
            }
            if (!label.isBlank() && label.length() <= MAX_POINT_LABEL_CHARS) {
                points.add(new GeneratedArticle.Point(label, pointText));
            }
        }
        return points;
    }

    private static String cleanHeadline(String headline) {
        String cleaned = headline.strip()
                .replaceFirst("(?i)^(headline|title)\\s*:\\s*", "")
                .replaceFirst("^[#*\\-\\s]+", "")
                .replaceAll("[*_]+$", "")
                .strip();
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).strip();
        }
        return cleaned;
    }
}
