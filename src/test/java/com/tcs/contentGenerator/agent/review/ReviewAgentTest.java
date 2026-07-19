package com.tcs.contentGenerator.agent.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.ShapeBox;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Covers the layout-lint findings (overflow, overlap, margin, orphaned
 * header), the deterministic quality score, and the "no design yet" skip
 * path, with the LLM stubbed — no Ollama needed.
 */
class ReviewAgentTest {

    private static final Theme THEME = new Theme(
            new PageSize(300, 400),
            Map.of("background", "#ffffff", "text", "#111111"),
            Map.of("Body", new TextStyle("SansSerif", 10, "normal", "text", 12)),
            new Spacing(20, 8));

    /** LLM stub returning a canned bare-array completion (or an empty one). */
    private static LlmClient llmReturning(EditorialCheck[] findings) {
        return new LlmClient() {
            @Override
            public String generate(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException("not used by this agent");
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
                return (T) findings;
            }
        };
    }

    private static ReviewAgent agent(LlmClient llm) {
        return new ReviewAgent(llm, 4.5, 1.0, 4.0);
    }

    private static PipelineContext contextWith(Page page) {
        PipelineContext context = new PipelineContext("job-1", List.of());
        DesignDocument document = new DesignDocument(1, 1, new DesignMeta("Issue", "job-1"), THEME,
                List.of(), List.of(page));
        context.setDesignDocument(document);
        return context;
    }

    @Test
    void cleanArticleScoresPerfectWithNoFindings() {
        TextBox body = new TextBox("cmp-1", ComponentRole.ARTICLE_BODY, new Frame(20, 20, 260, 40), 0, false,
                null, "Body", "A short, well-fitted article body.");
        PipelineContext context = contextWith(new Page("page-1", List.of(body)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        ReviewReport report = context.getReviewReport();
        assertEquals(100, report.qualityScore());
        assertTrue(report.findings().isEmpty());
        assertEquals(1, report.articlesChecked());
    }

    @Test
    void flagsTextOverflowWhenFrameIsTooShortForTheText() {
        String longText = "This sentence repeats several times to force line wrapping. ".repeat(10);
        TextBox body = new TextBox("cmp-1", ComponentRole.ARTICLE_BODY, new Frame(20, 20, 100, 12), 0, false,
                null, "Body", longText);
        PipelineContext context = contextWith(new Page("page-1", List.of(body)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        ReviewReport report = context.getReviewReport();
        assertTrue(report.findings().stream()
                .anyMatch(f -> f.category().equals("TEXT_OVERFLOW") && f.componentId().equals("cmp-1")));
        assertTrue(report.qualityScore() < 100);
    }

    @Test
    void flagsOverlappingTextFrames() {
        TextBox a = new TextBox("cmp-1", ComponentRole.ARTICLE_HEADLINE, new Frame(20, 20, 100, 40), 0, false,
                null, "Body", "Headline");
        TextBox b = new TextBox("cmp-2", ComponentRole.ARTICLE_BODY, new Frame(50, 40, 100, 40), 1, false,
                null, "Body", "Body text that overlaps the headline above it.");
        PipelineContext context = contextWith(new Page("page-1", List.of(a, b)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                .anyMatch(f -> f.category().equals("FRAME_OVERLAP")));
    }

    @Test
    void doesNotFlagDecorationImagesThatBleedAndSitBehindContent() {
        // full-bleed masthead band: outside the margins AND under the title — both exempt
        com.tcs.contentGenerator.design.ImageBox band = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-1", ComponentRole.DECORATION, new Frame(0, 0, 300, 80), 0, true,
                null, "decor-masthead-cmp-1", "masthead band");
        TextBox title = new TextBox("cmp-2", ComponentRole.ISSUE_TITLE, new Frame(20, 20, 260, 40), 1, false,
                null, "Body", "Issue title on the band");
        PipelineContext context = contextWith(new Page("page-1", List.of(band, title)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                        .noneMatch(f -> f.category().equals("FRAME_OVERLAP")
                                || f.category().equals("MARGIN_VIOLATION")),
                "decoration components must be exempt from overlap and margin lints, got "
                        + context.getReviewReport().findings());
    }

    @Test
    void textSittingOnADecorationIsExemptFromTheContrastLint() {
        // "Band" style is white-on-white against the PAGE — a guaranteed contrast
        // failure — but a DECORATION band sits behind it, whose pixel fill the
        // lint cannot judge, so the check must skip this box entirely.
        Theme bandTheme = new Theme(THEME.pageSize(), THEME.colors(),
                Map.of("Band", new TextStyle("SansSerif", 10, "bold", "background", 12)),
                THEME.spacing());
        com.tcs.contentGenerator.design.ImageBox band = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-1", ComponentRole.DECORATION, new Frame(0, 0, 300, 80), 0, true,
                null, "decor-masthead-cmp-1", "masthead band");
        TextBox title = new TextBox("cmp-2", ComponentRole.ISSUE_TITLE, new Frame(20, 20, 260, 40), 1, false,
                null, "Band", "White title on the band");
        PipelineContext context = new PipelineContext("job-1", List.of());
        context.setDesignDocument(new DesignDocument(1, 1, new DesignMeta("Issue", "job-1"), bandTheme,
                List.of(), List.of(new Page("page-1", List.of(band, title)))));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                        .noneMatch(f -> f.category().equals("LOW_CONTRAST")),
                "text over a decoration must not be contrast-checked, got "
                        + context.getReviewReport().findings());
    }

    @Test
    void trailingFooterDecorationDoesNotHideAnOrphanedHeader() {
        TextBox orphan = new TextBox("cmp-1", ComponentRole.SECTION_TITLE, new Frame(20, 370, 200, 16), 0, false,
                null, "Body", "Orphaned section title");
        com.tcs.contentGenerator.design.ImageBox footer = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-2", ComponentRole.DECORATION, new Frame(0, 392, 300, 8), 0, true,
                null, "decor-footer-cmp-2", "footer band");
        PipelineContext context = contextWith(new Page("page-1", List.of(orphan, footer)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                        .anyMatch(f -> f.category().equals("ORPHANED_HEADER")),
                "the orphan check must look past the trailing footer decoration");
    }

    @Test
    void doesNotFlagOverlapsInvolvingDecorativeShapes() {
        ShapeBox dot = new ShapeBox("cmp-1", ComponentRole.SECTION_ICON, new Frame(20, 20, 10, 10), 0, false,
                null, "circle", "primary");
        TextBox title = new TextBox("cmp-2", ComponentRole.SECTION_TITLE, new Frame(20, 20, 200, 20), 1, false,
                null, "Body", "Section title next to its icon dot");
        PipelineContext context = contextWith(new Page("page-1", List.of(dot, title)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                .noneMatch(f -> f.category().equals("FRAME_OVERLAP")));
    }

    @Test
    void doesNotFlagInfographicTextThatFitsCleanlyOnItsOwnShape() {
        com.tcs.contentGenerator.design.ImageBox shape = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-1", ComponentRole.DECORATION, new Frame(20, 20, 260, 30), 0, true,
                null, "decor-infographic-numberedBars.1-cmp-1", "infographic row");
        TextBox label = new TextBox("cmp-2", ComponentRole.INFOGRAPHIC_LABEL, new Frame(50, 24, 200, 12), 1, false,
                null, "Body", "Step one");
        PipelineContext context = contextWith(new Page("page-1", List.of(shape, label)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                        .noneMatch(f -> f.category().startsWith("INFOGRAPHIC_")),
                "infographic text fully inside its own shape must not be flagged, got "
                        + context.getReviewReport().findings());
    }

    @Test
    void flagsInfographicTextWithNoShapeBehindIt() {
        TextBox label = new TextBox("cmp-1", ComponentRole.INFOGRAPHIC_LABEL, new Frame(50, 24, 200, 12), 0, false,
                null, "Body", "Orphaned point");
        PipelineContext context = contextWith(new Page("page-1", List.of(label)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                .anyMatch(f -> f.category().equals("INFOGRAPHIC_TEXT_UNANCHORED") && f.componentId().equals("cmp-1")));
    }

    @Test
    void flagsInfographicTextThatSpillsOutsideItsShape() {
        com.tcs.contentGenerator.design.ImageBox shape = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-1", ComponentRole.DECORATION, new Frame(20, 20, 260, 20), 0, true,
                null, "decor-infographic-numberedBars.1-cmp-1", "infographic row");
        // taller than the shape it overlaps — the label/row-height math disagreed
        TextBox label = new TextBox("cmp-2", ComponentRole.INFOGRAPHIC_LABEL, new Frame(50, 24, 200, 40), 1, false,
                null, "Body", "Overflowing point");
        PipelineContext context = contextWith(new Page("page-1", List.of(shape, label)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                .anyMatch(f -> f.category().equals("INFOGRAPHIC_TEXT_OUTSIDE_SHAPE") && f.componentId().equals("cmp-2")));
    }

    @Test
    void flagsInfographicTextStraddlingTwoShapes() {
        com.tcs.contentGenerator.design.ImageBox shapeA = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-1", ComponentRole.DECORATION, new Frame(20, 20, 260, 20), 0, true,
                null, "decor-infographic-numberedBars.1-cmp-1", "infographic row");
        com.tcs.contentGenerator.design.ImageBox shapeB = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-2", ComponentRole.DECORATION, new Frame(20, 40, 260, 20), 1, true,
                null, "decor-infographic-numberedBars.2-cmp-2", "infographic row");
        TextBox label = new TextBox("cmp-3", ComponentRole.INFOGRAPHIC_LABEL, new Frame(50, 35, 200, 12), 2, false,
                null, "Body", "Straddling point");
        PipelineContext context = contextWith(new Page("page-1", List.of(shapeA, shapeB, label)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                .anyMatch(f -> f.category().equals("INFOGRAPHIC_TEXT_STRADDLES_SHAPES")
                        && f.componentId().equals("cmp-3")));
    }

    @Test
    void flagsTwoInfographicShapesThatOverlapEachOther() {
        com.tcs.contentGenerator.design.ImageBox shapeA = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-1", ComponentRole.DECORATION, new Frame(20, 20, 260, 30), 0, true,
                null, "decor-infographic-numberedBars.1-cmp-1", "infographic row");
        com.tcs.contentGenerator.design.ImageBox shapeB = new com.tcs.contentGenerator.design.ImageBox(
                "cmp-2", ComponentRole.DECORATION, new Frame(20, 40, 260, 30), 1, true,
                null, "decor-infographic-numberedBars.2-cmp-2", "infographic row");
        PipelineContext context = contextWith(new Page("page-1", List.of(shapeA, shapeB)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                .anyMatch(f -> f.category().equals("INFOGRAPHIC_SHAPE_OVERLAP") && f.componentId().equals("cmp-1")));
    }

    @Test
    void flagsComponentOutsidePageMargins() {
        TextBox body = new TextBox("cmp-1", ComponentRole.ARTICLE_BODY, new Frame(0, 20, 100, 20), 0, false,
                null, "Body", "Sits past the left margin.");
        PipelineContext context = contextWith(new Page("page-1", List.of(body)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                .anyMatch(f -> f.category().equals("MARGIN_VIOLATION")));
    }

    @Test
    void flagsSectionTitleOrphanedAtPageBottom() {
        TextBox title = new TextBox("cmp-1", ComponentRole.SECTION_TITLE, new Frame(20, 370, 200, 15), 0, false,
                null, "Body", "Upcoming Events");
        PipelineContext context = contextWith(new Page("page-1", List.of(title)));

        agent(llmReturning(new EditorialCheck[0])).execute(context);

        assertTrue(context.getReviewReport().findings().stream()
                .anyMatch(f -> f.category().equals("ORPHANED_HEADER")));
    }

    @Test
    void includesEditorialFindingsFromTheLlm() {
        TextBox body = new TextBox("cmp-1", ComponentRole.ARTICLE_BODY, new Frame(20, 20, 260, 40), 0, false,
                null, "Body", "This sentence have a grammar mistake.");
        PipelineContext context = contextWith(new Page("page-1", List.of(body)));

        agent(llmReturning(new EditorialCheck[] {
                new EditorialCheck("grammar", "medium", "\"have\" should be \"has\".")
        })).execute(context);

        ReviewReport report = context.getReviewReport();
        assertEquals(1, report.editorialFindingCount());
        ReviewFinding finding = report.findings().stream()
                .filter(f -> f.source() == FindingSource.EDITORIAL)
                .findFirst().orElseThrow();
        assertEquals("GRAMMAR", finding.category());
        assertEquals(FindingSeverity.MEDIUM, finding.severity());
        assertEquals("cmp-1", finding.componentId());
    }

    @Test
    void degradesToALowFindingWhenTheEditorialCallFails() {
        TextBox body = new TextBox("cmp-1", ComponentRole.ARTICLE_BODY, new Frame(20, 20, 260, 40), 0, false,
                null, "Body", "Some article text.");
        PipelineContext context = contextWith(new Page("page-1", List.of(body)));

        LlmClient failing = new LlmClient() {
            @Override
            public String generate(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
                throw new IllegalStateException("LLM unavailable");
            }
        };

        agent(failing).execute(context);

        ReviewReport report = context.getReviewReport();
        assertEquals(1, report.editorialFindingCount());
        assertEquals(FindingSeverity.LOW, report.findings().get(report.findings().size() - 1).severity());
    }

    @Test
    void skipsReviewWhenNoDesignDocumentExists() {
        PipelineContext context = new PipelineContext("job-1", List.of());
        agent(llmReturning(new EditorialCheck[0])).execute(context);
        assertNull(context.getReviewReport());
    }
}
