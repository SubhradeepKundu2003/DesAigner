package com.tcs.contentGenerator.agent.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Agent #9 of the pipeline, and the last one before export. A quality pass
 * over the finished {@link DesignDocument} — every earlier agent's work has
 * to actually read well and sit correctly on the page. Two independent halves:
 *
 * <ul>
 *   <li>{@link LayoutLint} — deterministic geometry/contrast checks, no LLM;</li>
 *   <li>one LLM call per {@code ARTICLE_BODY} text box, returning a bare
 *       {@link EditorialCheck} array of grammar/spelling/readability issues.</li>
 * </ul>
 *
 * <p>Nothing here rewrites the design or the newsletter — this agent only
 * reports; a human (via the future editor) or a rerun of an earlier agent
 * fixes what it finds. The 0-100 quality score is computed deterministically
 * from finding severities, never asked of the model.
 */
@Component
@Order(9)
public class ReviewAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ReviewAgent.class);

    private final LlmClient llm;
    private final LayoutLint layoutLint;

    public ReviewAgent(LlmClient llm,
            @Value("${app.review.contrast-ratio-threshold:4.5}") double contrastRatioThreshold,
            @Value("${app.review.margin-tolerance-pt:1.0}") double marginTolerancePt,
            @Value("${app.review.overflow-tolerance-pt:4.0}") double overflowTolerancePt) {
        this.llm = llm;
        this.layoutLint = new LayoutLint(contrastRatioThreshold, marginTolerancePt, overflowTolerancePt);
    }

    @Override
    public String name() {
        return "Review Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        DesignDocument document = context.getDesignDocument();
        if (document == null) {
            log.info("No design document to review, skipping review");
            return;
        }

        List<ReviewFinding> findings = new ArrayList<>(layoutLint.check(document));

        int componentsChecked = 0;
        int articlesChecked = 0;
        for (Page page : document.pages()) {
            componentsChecked += page.components().size();
            for (var component : page.components()) {
                if (component instanceof TextBox box && box.role() == ComponentRole.ARTICLE_BODY) {
                    articlesChecked++;
                    findings.addAll(reviewText(box));
                }
            }
        }

        int qualityScore = scoreOf(findings);
        context.setReviewReport(new ReviewReport(qualityScore, findings, componentsChecked, articlesChecked));
        log.info("Review found {} finding(s) across {} component(s) ({} article(s) editorially checked); "
                + "quality score {}", findings.size(), componentsChecked, articlesChecked, qualityScore);
    }

    private List<ReviewFinding> reviewText(TextBox box) {
        try {
            EditorialCheck[] found = llm.generate(ReviewPrompts.SYSTEM,
                    ReviewPrompts.USER_TEMPLATE.formatted(box.text()), EditorialCheck[].class);
            return toFindings(found, box.id());
        } catch (Exception e) {
            log.warn("Editorial review call failed for component {}: {}", box.id(), e.toString());
            return List.of(new ReviewFinding(FindingSource.EDITORIAL, "REVIEW_FAILED", FindingSeverity.LOW,
                    box.id(), "Automated editorial review could not run for this article."));
        }
    }

    /** Maps the model's findings onto {@link ReviewFinding}s, null-guarding every field. */
    private static List<ReviewFinding> toFindings(EditorialCheck[] found, String componentId) {
        if (found == null) {
            return List.of();
        }
        List<ReviewFinding> out = new ArrayList<>();
        for (EditorialCheck raw : found) {
            if (raw == null) {
                continue;
            }
            String issue = raw.issue() == null ? "" : raw.issue().strip();
            if (issue.isBlank()) {
                continue;
            }
            String category = raw.category() == null || raw.category().isBlank()
                    ? "READABILITY"
                    : raw.category().strip().toUpperCase(Locale.ROOT);
            out.add(new ReviewFinding(FindingSource.EDITORIAL, category,
                    FindingSeverity.fromLabel(raw.severity()), componentId, issue));
        }
        return out;
    }

    private static int scoreOf(List<ReviewFinding> findings) {
        int score = 100;
        for (ReviewFinding f : findings) {
            score -= switch (f.severity()) {
                case HIGH -> 10;
                case MEDIUM -> 5;
                case LOW -> 2;
            };
        }
        return Math.max(0, Math.min(100, score));
    }
}
