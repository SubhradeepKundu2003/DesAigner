package com.tcs.contentGenerator.agent.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(), "assets");
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
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(), "custom-root");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        Asset logo = context.getDesignDocument().assets().get(0);
        assertEquals("custom-root/BRAND/logo_black.svg", logo.storedRef());
    }

    @Test
    void usesCatalogDefaultTemplate() {
        DesignCompositionAgent agent = new DesignCompositionAgent(TEMPLATES, new LayoutEngine(), "assets");
        PipelineContext context = contextWithNewsletter();

        agent.execute(context);

        assertTrue(context.getDesignDocument().theme().colors().values().contains("#4E84C4"),
                "expected the design to use the tcs-brand theme (TCS Blue present)");
        assertEquals(TEMPLATES.getDefault().theme(), context.getDesignDocument().theme());
    }
}
