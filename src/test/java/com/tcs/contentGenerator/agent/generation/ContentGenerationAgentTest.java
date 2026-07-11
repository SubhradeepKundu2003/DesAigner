package com.tcs.contentGenerator.agent.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.planning.NewsletterPlan;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.agent.planning.PlannedItem;
import com.tcs.contentGenerator.agent.planning.SectionPlan;
import com.tcs.contentGenerator.agent.understanding.BusinessCategory;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.agent.understanding.ItemType;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Guards the plain-text protocol parser — specifically the optional
 * {@code POINT: label | text} lines feeding infographic selection: they must
 * be pulled out of the body wherever the small model puts them, tolerated in
 * their common malformed shapes, and never invented.
 */
class ContentGenerationAgentTest {

    private static PipelineContext contextWithOneItem() {
        ContentItem item = new ContentItem("Apollo rollout", "The Apollo program shipped.",
                BusinessCategory.PROJECT_UPDATES, ItemType.PROJECT, List.of(), List.of());
        NewsletterPlan plan = new NewsletterPlan("Test Issue", List.of(
                new SectionPlan(NewsletterSection.PROJECT_UPDATES,
                        List.of(new PlannedItem(item, 8, "notable")))),
                List.of());
        PipelineContext context = new PipelineContext("job-1", List.of());
        context.setNewsletterPlan(plan);
        return context;
    }

    private static GeneratedArticle generateWith(String cannedResponse) {
        ContentGenerationAgent agent = new ContentGenerationAgent(new CannedLlm(cannedResponse));
        PipelineContext context = contextWithOneItem();
        agent.execute(context);
        return context.getGeneratedNewsletter().sections().get(0).articles().get(0);
    }

    @Test
    void pointLinesAreParsedAndStrippedFromTheBody() {
        GeneratedArticle article = generateWith("""
                Apollo Lands On Time

                The Apollo program shipped this month across three workstreams.

                POINT: Discovery complete | Requirements were signed off by all teams.
                POINT: Build phase | Development finished two weeks early.
                POINT: Go-live | The rollout reached every region.""");

        assertEquals("Apollo Lands On Time", article.headline());
        assertEquals(3, article.points().size());
        assertEquals("Discovery complete", article.points().get(0).label());
        assertEquals("Requirements were signed off by all teams.", article.points().get(0).text());
        assertFalse(article.body().contains("POINT:"), "protocol lines must not leak into prose");
        assertTrue(article.body().contains("three workstreams"));
    }

    @Test
    void articleWithoutPointLinesHasNoPoints() {
        GeneratedArticle article = generateWith("""
                Apollo Lands On Time

                Just ordinary prose without any enumerable structure.""");
        assertTrue(article.points().isEmpty());
    }

    @Test
    void malformedPointLinesAreToleratedNotGuessed() {
        GeneratedArticle article = generateWith("""
                Headline Here

                Body prose.

                - POINT: Bulleted label | The model added a bullet anyway.
                POINT: %s
                POINT: Label without pipe""".formatted("x".repeat(120)));

        // bullet-prefixed line parses; the 120-char "label" is a sentence the
        // model failed to split (dropped); a bare short label is kept with empty text
        assertEquals(List.of("Bulleted label", "Label without pipe"),
                article.points().stream().map(GeneratedArticle.Point::label).toList());
        assertEquals("", article.points().get(1).text());
    }

    @Test
    void pointCountIsCappedAtSix() {
        StringBuilder response = new StringBuilder("Headline\n\nBody.\n");
        for (int i = 1; i <= 9; i++) {
            response.append("POINT: Item ").append(i).append(" | text\n");
        }
        assertEquals(6, generateWith(response.toString()).points().size());
    }

    @Test
    void failedGenerationFallsBackToExtractedTextWithNoPoints() {
        ContentGenerationAgent agent = new ContentGenerationAgent(new CannedLlm(null) {
            @Override
            public String generate(String system, String user) {
                throw new IllegalStateException("model down");
            }
        });
        PipelineContext context = contextWithOneItem();
        agent.execute(context);
        GeneratedArticle article = context.getGeneratedNewsletter().sections().get(0).articles().get(0);
        assertEquals("Apollo rollout", article.headline());
        assertTrue(article.points().isEmpty());
    }

    /** Text-only stub returning one canned completion. */
    private static class CannedLlm implements LlmClient {
        private final String response;

        CannedLlm(String response) {
            this.response = response;
        }

        @Override
        public String generate(String system, String user) {
            return response;
        }

        @Override
        public <T> T generate(String system, String user, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }
    }
}
