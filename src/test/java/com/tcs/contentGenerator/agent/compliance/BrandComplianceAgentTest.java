package com.tcs.contentGenerator.agent.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Covers the deterministic fix paths (terminology substitution, name casing)
 * and the rewrite acceptance rules, with the LLM stubbed — no Ollama needed.
 */
class BrandComplianceAgentTest {

    private static final ComplianceRules RULES = new ComplianceRules(
            Map.of("e-mail", "email", "utilize", "use", "in order to", "to"),
            List.of("PowerPoint", "TD Monthly"),
            List.of("synergy", "best-in-class"));

    /** LLM stub returning a canned completion (or throwing when {@code null}). */
    private static LlmClient llmReturning(String completion) {
        return new LlmClient() {
            @Override
            public String generate(String systemPrompt, String userPrompt) {
                if (completion == null) {
                    throw new IllegalStateException("LLM unavailable");
                }
                return completion;
            }

            @Override
            public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
                throw new UnsupportedOperationException("not used by this agent");
            }
        };
    }

    private static PipelineContext contextWith(String headline, String body) {
        PipelineContext context = new PipelineContext("test-job", List.of());
        context.setGeneratedNewsletter(new GeneratedNewsletter("TD Monthly — July 2026",
                List.of(new GeneratedSection(NewsletterSection.DELIVERY_HIGHLIGHTS,
                        List.of(new GeneratedArticle(headline, body, null))))));
        return context;
    }

    private static GeneratedArticle articleOf(PipelineContext context) {
        return context.getGeneratedNewsletter().sections().get(0).articles().get(0);
    }

    @Test
    void fixesTerminologyPreservingCase() {
        PipelineContext context = contextWith("Utilize the new portal",
                "Teams utilize e-mail in order to share updates.");
        new BrandComplianceAgent(llmReturning(null), RULES).execute(context);

        GeneratedArticle fixed = articleOf(context);
        assertEquals("Use the new portal", fixed.headline());
        assertEquals("Teams use email to share updates.", fixed.body());
        ComplianceReport report = context.getComplianceReport();
        assertEquals(1, report.articlesFixed());
        assertTrue(report.violations().stream()
                .allMatch(v -> v.type() == ViolationType.TERMINOLOGY && v.fixed()));
    }

    @Test
    void fixesProperNameCasing() {
        PipelineContext context = contextWith("Powerpoint tips",
                "The td monthly team shared POWERPOINT templates.");
        new BrandComplianceAgent(llmReturning(null), RULES).execute(context);

        GeneratedArticle fixed = articleOf(context);
        assertEquals("PowerPoint tips", fixed.headline());
        assertEquals("The TD Monthly team shared PowerPoint templates.", fixed.body());
        assertTrue(context.getComplianceReport().violations().stream()
                .allMatch(v -> v.type() == ViolationType.CASING && v.fixed()));
    }

    @Test
    void acceptsRewriteThatRemovesBannedPhrase() {
        PipelineContext context = contextWith("A best-in-class launch",
                "The rollout created synergy across 3 teams.");
        new BrandComplianceAgent(llmReturning("""
                A strong launch

                The rollout brought 3 teams closer together."""), RULES).execute(context);

        GeneratedArticle fixed = articleOf(context);
        assertEquals("A strong launch", fixed.headline());
        assertEquals("The rollout brought 3 teams closer together.", fixed.body());
        assertEquals(0, context.getComplianceReport().unresolvedCount());
    }

    @Test
    void rejectsRewriteThatInventsFigures() {
        PipelineContext context = contextWith("A best-in-class launch",
                "The rollout created synergy across 3 teams.");
        new BrandComplianceAgent(llmReturning("""
                A strong launch

                The rollout brought 17 teams closer together."""), RULES).execute(context);

        // Original text kept, breach reported for a human editor.
        assertEquals("The rollout created synergy across 3 teams.", articleOf(context).body());
        ComplianceReport report = context.getComplianceReport();
        assertEquals(2, report.unresolvedCount());
        assertFalse(report.violations().stream().anyMatch(ComplianceViolation::fixed));
    }

    @Test
    void reportsUnresolvedWhenRewriteCallFails() {
        PipelineContext context = contextWith("Headline",
                "This quarter's paradigm shift... just kidding: pure synergy.");
        new BrandComplianceAgent(llmReturning(null), RULES).execute(context);

        assertEquals(1, context.getComplianceReport().unresolvedCount());
        assertEquals("This quarter's paradigm shift... just kidding: pure synergy.",
                articleOf(context).body());
    }

    @Test
    void cleanArticleIsUntouched() {
        PipelineContext context = contextWith("All good here",
                "Nothing to fix in this text.");
        new BrandComplianceAgent(llmReturning(null), RULES).execute(context);

        ComplianceReport report = context.getComplianceReport();
        assertEquals(1, report.articlesChecked());
        assertEquals(0, report.articlesFixed());
        assertTrue(report.violations().isEmpty());
    }
}
