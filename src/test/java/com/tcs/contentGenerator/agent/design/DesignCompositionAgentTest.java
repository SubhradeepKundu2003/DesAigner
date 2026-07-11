package com.tcs.contentGenerator.agent.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.design.layout.LayoutEngine;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.agent.planning.PlannedItem;
import com.tcs.contentGenerator.agent.understanding.BusinessCategory;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.agent.understanding.ItemType;
import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.storage.StorageService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the asset attachments this agent owns: the brand logo (always
 * present, theme-aware black/white variant), section icons (present iff a
 * file exists and the page is light), and — since the decor layer — one
 * generated SVG asset per decoration {@code ImageBox} the layout placed.
 */
class DesignCompositionAgentTest {

    private static final TemplateCatalog TEMPLATES =
            new TemplateCatalog(JsonMapper.builder().build(), "tcs-brand");
    /** Same catalog, dark-background template active — exercises the dark-theme branches. */
    private static final TemplateCatalog DARK_TEMPLATES =
            new TemplateCatalog(JsonMapper.builder().build(), "noir-luxe");
    /** td-classic has no decor — the pre-decor plain baseline. */
    private static final TemplateCatalog PLAIN_TEMPLATES =
            new TemplateCatalog(JsonMapper.builder().build(), "td-classic");
    private static final com.tcs.contentGenerator.agent.design.infographic.InfographicCatalog INFOGRAPHICS =
            new com.tcs.contentGenerator.agent.design.infographic.InfographicCatalog(JsonMapper.builder().build());

    private static PipelineContext contextWithNewsletter() {
        GeneratedArticle article = new GeneratedArticle("Delivery headline",
                "A short delivery highlights body with a couple of sentences.", null);
        GeneratedSection section = new GeneratedSection(NewsletterSection.DELIVERY_HIGHLIGHTS, List.of(article));
        GeneratedNewsletter newsletter = new GeneratedNewsletter("Test Issue", List.of(section));

        PipelineContext context = new PipelineContext("job-1", List.of());
        context.setGeneratedNewsletter(newsletter);
        return context;
    }

    private static Asset assetById(DesignDocument document, String id) {
        return document.assets().stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("expected asset " + id + ", got "
                        + document.assets().stream().map(Asset::id).toList()));
    }

    @Test
    void attachesUnconditionalBrandLogoAsset() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), INFOGRAPHICS, "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        Asset logo = assetById(context.getDesignDocument(), LayoutEngine.BRAND_LOGO_ASSET_ID);
        // tcs-brand's masthead band starts on secondary (TCS yellow, light) -> black logo
        assertEquals("assets/BRAND/logo_black.svg", logo.storedRef());
    }

    @Test
    void logoStoredRefRespectsBrandAssetsRootProperty() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), INFOGRAPHICS, "custom-root");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        Asset logo = assetById(context.getDesignDocument(), LayoutEngine.BRAND_LOGO_ASSET_ID);
        assertEquals("custom-root/BRAND/logo_black.svg", logo.storedRef());
    }

    @Test
    void usesCatalogDefaultTemplate() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), INFOGRAPHICS, "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        assertTrue(context.getDesignDocument().theme().colors().values().contains("#4E84C4"),
                "expected the design to use the tcs-brand theme (TCS Blue present)");
        assertEquals(TEMPLATES.getDefault().theme(), context.getDesignDocument().theme());
    }

    @Test
    void attachesIconAssetWhenSectionIconFileExists() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of(
                        "assets/ICONS/DELIVERY_HIGHLIGHTS.svg",
                        "assets/ICONS/readme.txt")),
                INFOGRAPHICS, "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        Asset icon = assetById(context.getDesignDocument(), "icon-DELIVERY_HIGHLIGHTS");
        assertEquals("assets/ICONS/DELIVERY_HIGHLIGHTS.svg", icon.storedRef());
    }

    @Test
    void sectionWithoutIconFileKeepsNoIconAsset() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of("assets/ICONS/UPCOMING_EVENTS.svg")), INFOGRAPHICS, "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        // The newsletter only has DELIVERY_HIGHLIGHTS; the events icon is unused.
        assertTrue(context.getDesignDocument().assets().stream().noneMatch(a -> a.id().startsWith("icon-")),
                "no icon asset expected for an unused section icon file");
    }

    @Test
    void darkTemplateGetsTheWhiteLogoVariant() {
        DesignCompositionAgent agent = new DesignCompositionAgent(DARK_TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), INFOGRAPHICS, "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        // noir-luxe's masthead starts on secondary (deep blue, dark) -> white logo
        Asset logo = assetById(context.getDesignDocument(), LayoutEngine.BRAND_LOGO_ASSET_ID);
        assertEquals("assets/BRAND/logo_white.svg", logo.storedRef());
    }

    @Test
    void darkTemplateSkipsBlackIconFilesAndKeepsDotFallback() {
        DesignCompositionAgent agent = new DesignCompositionAgent(DARK_TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of("assets/ICONS/DELIVERY_HIGHLIGHTS.svg")), INFOGRAPHICS, "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        // the icon file exists, but black strokes are invisible on #121214
        assertTrue(context.getDesignDocument().assets().stream().noneMatch(a -> a.id().startsWith("icon-")),
                "no icon assets expected on a dark page background");
    }

    @Test
    void everyDecorationBoxGetsAStoredSvgAsset() {
        ListingStorage storage = new ListingStorage(List.of());
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                storage, INFOGRAPHICS, "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        DesignDocument document = context.getDesignDocument();
        // DECORATION ShapeBoxes (cover background, tint bands) and empty photo
        // slots (assetId null, filled later by the graphics agent) need no
        // generated asset — only decor-* ImageBoxes do
        List<ImageBox> decorations = document.pages().stream()
                .flatMap(p -> p.components().stream())
                .filter(c -> c.role() == ComponentRole.DECORATION)
                .filter(ImageBox.class::isInstance)
                .map(ImageBox.class::cast)
                .filter(box -> box.assetId() != null)
                .toList();
        assertTrue(!decorations.isEmpty(), "tcs-brand has decor — expected decoration boxes");
        for (ImageBox box : decorations) {
            Asset asset = assetById(document, box.assetId());
            String svg = new String(storage.stored.get(asset.storedRef()), StandardCharsets.UTF_8);
            assertTrue(svg.startsWith("<svg"), "stored decor asset must be an SVG: " + asset.storedRef());
            assertTrue(asset.storedRef().startsWith("jobs/job-1/decor/"),
                    "decor assets live under the job's decor folder");
        }
    }

    @Test
    void singleArticleWithMultipleMetricsBecomesAKpiTileRow() {
        ContentItem item = new ContentItem("Delivery", "summary", BusinessCategory.DELIVERY_HIGHLIGHTS,
                ItemType.METRIC, List.of("NPS: 72", "Growth 18%", "Rating 4.6/5"), List.of());
        GeneratedArticle article = new GeneratedArticle("Delivery headline",
                "A short delivery highlights body.", new PlannedItem(item, 9, "high impact"));
        GeneratedSection section = new GeneratedSection(NewsletterSection.DELIVERY_HIGHLIGHTS, List.of(article));
        PipelineContext context = new PipelineContext("job-1", List.of());
        context.setGeneratedNewsletter(new GeneratedNewsletter("Test Issue", List.of(section)));

        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), INFOGRAPHICS, "assets");
        agent.execute(context);

        // three numeric metrics → three KPI tiles, each a STAT_VALUE box; the
        // figure (with its percent) is kept, the rest becomes the label
        List<String> values = context.getDesignDocument().pages().stream()
                .flatMap(p -> p.components().stream())
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(b -> b.role() == ComponentRole.STAT_VALUE)
                .map(TextBox::text).toList();
        assertEquals(List.of("72", "18%", "4.6/5"), values,
                "each numeric metric becomes a KPI tile value (percent and ratio kept whole)");
        // each tile sits on its own stat-card decoration (reused machinery)
        long tileCards = context.getDesignDocument().pages().stream()
                .flatMap(p -> p.components().stream())
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(b -> b.assetId() != null && b.assetId().startsWith("decor-statcard-"))
                .count();
        assertEquals(3, tileCards, "one stat-card decoration per KPI tile");
    }

    @Test
    void articleWithPointsBecomesAnInfographicWithStoredRowAssets() {
        ContentItem item = new ContentItem("Apollo", "summary", BusinessCategory.PROJECT_UPDATES,
                ItemType.PROJECT, List.of(), List.of());
        GeneratedArticle article = new GeneratedArticle("Apollo lands on time",
                "A short project body.", new PlannedItem(item, 9, "notable"),
                List.of(new GeneratedArticle.Point("Discovery", "Requirements signed off."),
                        new GeneratedArticle.Point("Build", "Development finished early."),
                        new GeneratedArticle.Point("Go-live", "Rollout reached every region.")));
        GeneratedSection section = new GeneratedSection(NewsletterSection.PROJECT_UPDATES, List.of(article));
        PipelineContext context = new PipelineContext("job-1", List.of());
        context.setGeneratedNewsletter(new GeneratedNewsletter("Test Issue", List.of(section)));

        ListingStorage storage = new ListingStorage(List.of());
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                storage, INFOGRAPHICS, "assets");
        agent.execute(context);

        DesignDocument document = context.getDesignDocument();
        List<ImageBox> rows = document.pages().stream()
                .flatMap(p -> p.components().stream())
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(b -> b.assetId() != null && b.assetId().startsWith("decor-infographic-"))
                .toList();
        assertEquals(3, rows.size(), "three points → three painted numbered-bar rows");
        for (ImageBox row : rows) {
            Asset asset = assetById(document, row.assetId());
            String svg = new String(storage.stored.get(asset.storedRef()), StandardCharsets.UTF_8);
            assertTrue(svg.startsWith("<svg") && svg.contains("<circle"),
                    "each row asset is a painted SVG with its numbered disc: " + asset.storedRef());
        }
        // three labels as text (selectable, editable), none as ARTICLE_BODY
        long labels = document.pages().stream().flatMap(p -> p.components().stream())
                .filter(c -> c.role() == ComponentRole.INFOGRAPHIC_LABEL).count();
        assertEquals(3, labels, "each point label is a real text box");
    }

    @Test
    void twoPointsAreNotEnoughForAnInfographic() {
        ContentItem item = new ContentItem("Apollo", "summary", BusinessCategory.PROJECT_UPDATES,
                ItemType.PROJECT, List.of(), List.of());
        GeneratedArticle article = new GeneratedArticle("Apollo lands on time",
                "A short project body.", new PlannedItem(item, 9, "notable"),
                List.of(new GeneratedArticle.Point("Discovery", "done"),
                        new GeneratedArticle.Point("Build", "done")));
        GeneratedSection section = new GeneratedSection(NewsletterSection.PROJECT_UPDATES, List.of(article));
        PipelineContext context = new PipelineContext("job-1", List.of());
        context.setGeneratedNewsletter(new GeneratedNewsletter("Test Issue", List.of(section)));

        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), INFOGRAPHICS, "assets");
        agent.execute(context);

        assertTrue(context.getDesignDocument().pages().stream()
                        .flatMap(p -> p.components().stream())
                        .noneMatch(c -> c instanceof ImageBox box && box.assetId() != null
                                && box.assetId().startsWith("decor-infographic-")),
                "two points fall back to the plain patterns — an infographic is earned, not forced");
    }

    @Test
    void plainTemplateAttachesNoDecorAssets() {
        DesignCompositionAgent agent = new DesignCompositionAgent(PLAIN_TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), INFOGRAPHICS, "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        assertEquals(1, context.getDesignDocument().assets().size(),
                "td-classic (no decor) should attach exactly the logo asset");
    }

    /** Fake storage: {@code list} returns a fixed listing, {@code store} records, rest unreachable. */
    private static final class ListingStorage implements StorageService {
        private final List<String> refs;
        final Map<String, byte[]> stored = new HashMap<>();

        ListingStorage(List<String> refs) {
            this.refs = refs;
        }

        @Override
        public String store(String relativePath, byte[] content) {
            stored.put(relativePath, content);
            return relativePath;
        }

        @Override
        public byte[] retrieve(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path resolve(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> list(String relativeDir) {
            return refs;
        }
    }
}
