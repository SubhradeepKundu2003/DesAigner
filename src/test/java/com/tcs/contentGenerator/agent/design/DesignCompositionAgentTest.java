package com.tcs.contentGenerator.agent.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.design.layout.LayoutEngine;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.storage.StorageService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the brand-logo attachment: it must always be there (so the masthead's
 * {@code LOGO} ImageBox, added unconditionally in {@link LayoutEngine}, always
 * resolves an asset — falling back to the renderer's dashed placeholder is
 * expected until a real file is dropped in, but the {@link Asset} entry itself
 * must never be missing), and it must respect the same
 * {@code app.graphics.brand-assets-root} convention {@code AssetLibrary} uses.
 */
class DesignCompositionAgentTest {

    private static final TemplateCatalog TEMPLATES = new TemplateCatalog(JsonMapper.builder().build());

    private static PipelineContext contextWithNewsletter() {
        GeneratedArticle article = new GeneratedArticle("Delivery headline",
                "A short delivery highlights body with a couple of sentences.", null);
        GeneratedSection section = new GeneratedSection(NewsletterSection.DELIVERY_HIGHLIGHTS, List.of(article));
        GeneratedNewsletter newsletter = new GeneratedNewsletter("Test Issue", List.of(section));

        PipelineContext context = new PipelineContext("job-1", List.of());
        context.setGeneratedNewsletter(newsletter);
        return context;
    }

    @Test
    void attachesUnconditionalBrandLogoAsset() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        DesignDocument document = context.getDesignDocument();
        assertEquals(1, document.assets().size());
        Asset logo = document.assets().get(0);
        assertEquals(LayoutEngine.BRAND_LOGO_ASSET_ID, logo.id());
        assertEquals("assets/BRAND/logo_black.svg", logo.storedRef());
    }

    @Test
    void logoStoredRefRespectsBrandAssetsRootProperty() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), "custom-root");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        Asset logo = context.getDesignDocument().assets().get(0);
        assertEquals("custom-root/BRAND/logo_black.svg", logo.storedRef());
    }

    @Test
    void usesCatalogDefaultTemplate() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of()), "assets");
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
                "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        DesignDocument document = context.getDesignDocument();
        assertEquals(2, document.assets().size(), "expected logo + one section icon");
        Asset icon = document.assets().stream()
                .filter(a -> a.id().equals("icon-DELIVERY_HIGHLIGHTS"))
                .findFirst().orElseThrow(() -> new AssertionError("expected an icon asset for the section"));
        assertEquals("assets/ICONS/DELIVERY_HIGHLIGHTS.svg", icon.storedRef());
    }

    @Test
    void sectionWithoutIconFileKeepsNoIconAsset() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(),
                new ListingStorage(List.of("assets/ICONS/UPCOMING_EVENTS.svg")), "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        // The newsletter only has DELIVERY_HIGHLIGHTS; the events icon is unused.
        assertEquals(1, context.getDesignDocument().assets().size(), "only the logo should be attached");
    }

    /** Fake storage: {@code list} returns a fixed listing, everything else is unreachable. */
    private record ListingStorage(List<String> refs) implements StorageService {
        @Override
        public String store(String relativePath, byte[] content) {
            throw new UnsupportedOperationException();
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
