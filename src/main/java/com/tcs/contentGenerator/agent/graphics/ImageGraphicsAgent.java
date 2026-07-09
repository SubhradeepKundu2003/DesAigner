package com.tcs.contentGenerator.agent.graphics;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.design.DesignTemplate;
import com.tcs.contentGenerator.agent.design.TemplateCatalog;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Agent #8 of the pipeline. Enriches the positioned {@link DesignDocument}
 * from Design Composition with real images: for each article, prefers a
 * picture extracted from its own source document (matched via the same
 * document-level provenance fact validation already uses), falling back to an
 * approved brand asset for its section, or leaving the article as plain text
 * if neither is available. No {@code ImageBox} exists in the design until
 * this agent runs — it both decides where an image fits (a deterministic
 * geometry heuristic, see {@link ImagePlacer}) and fills it, so composition
 * and layout stay untouched.
 */
@Component
@Order(8)
public class ImageGraphicsAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ImageGraphicsAgent.class);

    private final StorageService storage;
    private final AssetLibrary assetLibrary;
    private final TemplateCatalog templates;

    public ImageGraphicsAgent(StorageService storage, AssetLibrary assetLibrary, TemplateCatalog templates) {
        this.storage = storage;
        this.assetLibrary = assetLibrary;
        this.templates = templates;
    }

    @Override
    public String name() {
        return "Image & Graphics Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        DesignDocument document = context.getDesignDocument();
        if (document == null) {
            log.info("No design document to enrich, skipping image graphics");
            return;
        }

        // Same template the composition agent laid the design out with; its
        // photo decor (if any) drives crop/clip/shadow treatment.
        DesignTemplate template = templates.getDefault();
        ImagePlacer placer = new ImagePlacer(context.getGeneratedNewsletter(), context.getDocuments(),
                document.assets(), storage, assetLibrary,
                template.decor() == null ? null : template.decor().photo(), context.getJobId());
        List<Page> pages = document.pages().stream()
                .map(page -> placer.enrichPage(page, document.theme()))
                .toList();

        DesignDocument enriched = new DesignDocument(document.schemaVersion(), document.revision(),
                document.meta(), document.theme(), placer.assets(), pages);
        context.setDesignDocument(enriched);
        context.setGraphicsReport(new GraphicsReport(placer.articlesConsidered(), placer.placements().size(),
                placer.placements()));
        log.info("Image graphics placed {} image(s) across {} candidate article(s)",
                placer.placements().size(), placer.articlesConsidered());
    }
}
