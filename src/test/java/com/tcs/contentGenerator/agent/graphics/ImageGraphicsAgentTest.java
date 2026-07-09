package com.tcs.contentGenerator.agent.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.agent.planning.PlannedItem;
import com.tcs.contentGenerator.agent.understanding.BusinessCategory;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.agent.understanding.ItemType;
import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.Component;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.SourceLink;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.document.DocumentMetadata;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.DocumentType;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.SourceRef;
import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Covers the three outcomes {@link ImagePlacer} can reach for an article:
 * a matching source-document image wins, a brand asset fills in when there
 * is no source image, and the article is left untouched when neither is
 * available or there isn't enough free space to fit an image.
 */
class ImageGraphicsAgentTest {

    /** td-classic has no decor, so photos stay untreated — the behavior under test here. */
    private static final com.tcs.contentGenerator.agent.design.TemplateCatalog PLAIN_TEMPLATES =
            new com.tcs.contentGenerator.agent.design.TemplateCatalog(
                    tools.jackson.databind.json.JsonMapper.builder().build(), "td-classic");

    private static final Theme THEME = new Theme(
            new PageSize(300, 400), Map.of(), Map.of(), new Spacing(20, 8));
    private static final SourceLink LINK = new SourceLink("Delivery Highlights", "NPS climbs to 72");

    @Test
    void placesSourceDocumentImageBelowTheArticleBody() throws IOException {
        TextBox body = bodyBox(100, 40, LINK);
        Page page = new Page("page-1", List.of(body));
        DesignDocument document = documentOf(page);

        DocumentModel doc = documentModelWith("report.docx",
                new ImageBlock("jobs/job-1/images/photo.png", null, "image/png",
                        new SourceRef("report.docx", "Body", 5)));
        ContentItem item = contentItem(new SourceRef("report.docx", "Body", 2));
        GeneratedNewsletter newsletter = newsletterWith(item);

        PipelineContext context = contextWith(document, newsletter, List.of(doc));
        byte[] png = tinyPng(4, 2);
        StorageService storage = new FakeStorageService(Map.of("jobs/job-1/images/photo.png", png), Map.of());

        new ImageGraphicsAgent(storage, new AssetLibrary(storage, "assets"), PLAIN_TEMPLATES).execute(context);

        DesignDocument result = context.getDesignDocument();
        List<Component> components = result.pages().get(0).components();
        assertEquals(2, components.size());
        ImageBox added = (ImageBox) components.get(1);
        assertEquals(LINK, added.source());
        Asset asset = result.assets().stream().filter(a -> a.id().equals(added.assetId())).findFirst().orElseThrow();
        assertEquals("jobs/job-1/images/photo.png", asset.storedRef());

        // Image sits below the body, doesn't overlap it, and stays within the body's column width.
        assertTrue(added.frame().y() >= body.frame().y() + body.frame().h());
        assertTrue(added.frame().w() <= body.frame().w() + 0.01);

        GraphicsReport report = context.getGraphicsReport();
        assertEquals(1, report.articlesConsidered());
        assertEquals(1, report.imagesPlaced());
        assertEquals(ImageSource.SOURCE_DOCUMENT, report.placements().get(0).source());
    }

    @Test
    void fallsBackToBrandAssetWhenNoSourceImageMatches() throws IOException {
        TextBox body = bodyBox(100, 40, LINK);
        Page page = new Page("page-1", List.of(body));
        DesignDocument document = documentOf(page);

        // ContentItem points at a document that was never ingested with any image.
        ContentItem item = contentItem(new SourceRef("other.docx", "Body", 2));
        GeneratedNewsletter newsletter = newsletterWith(item);
        PipelineContext context = contextWith(document, newsletter, List.of());

        byte[] png = tinyPng(2, 2);
        StorageService storage = new FakeStorageService(
                Map.of("assets/DELIVERY_HIGHLIGHTS/logo.png", png),
                Map.of("assets/DELIVERY_HIGHLIGHTS", List.of("assets/DELIVERY_HIGHLIGHTS/logo.png")));

        new ImageGraphicsAgent(storage, new AssetLibrary(storage, "assets"), PLAIN_TEMPLATES).execute(context);

        GraphicsReport report = context.getGraphicsReport();
        assertEquals(1, report.imagesPlaced());
        assertEquals(ImageSource.BRAND_ASSET, report.placements().get(0).source());
        assertEquals("assets/DELIVERY_HIGHLIGHTS/logo.png", report.placements().get(0).storedRef());
    }

    @Test
    void leavesArticleUntouchedWhenThereIsNoRoomForAnImage() {
        TextBox body = bodyBox(100, 40, LINK);
        // Next component starts just after the body with no meaningful gap.
        TextBox next = new TextBox("cmp-2", ComponentRole.ARTICLE_HEADLINE,
                new Frame(20, 145, 260, 20), 1, false, null, "Headline", "Next story");
        Page page = new Page("page-1", List.of(body, next));
        DesignDocument document = documentOf(page);

        ContentItem item = contentItem(new SourceRef("other.docx", "Body", 2));
        GeneratedNewsletter newsletter = newsletterWith(item);
        PipelineContext context = contextWith(document, newsletter, List.of());

        StorageService storage = new FakeStorageService(Map.of(), Map.of());
        new ImageGraphicsAgent(storage, new AssetLibrary(storage, "assets"), PLAIN_TEMPLATES).execute(context);

        assertEquals(2, context.getDesignDocument().pages().get(0).components().size());
        assertEquals(0, context.getGraphicsReport().imagesPlaced());
        assertEquals(1, context.getGraphicsReport().articlesConsidered());
    }

    @Test
    void leavesArticleUntouchedWhenNoImageIsAvailableAtAll() {
        TextBox body = bodyBox(100, 40, LINK);
        Page page = new Page("page-1", List.of(body));
        DesignDocument document = documentOf(page);

        ContentItem item = contentItem(new SourceRef("other.docx", "Body", 2));
        GeneratedNewsletter newsletter = newsletterWith(item);
        PipelineContext context = contextWith(document, newsletter, List.of());

        StorageService storage = new FakeStorageService(Map.of(), Map.of());
        new ImageGraphicsAgent(storage, new AssetLibrary(storage, "assets"), PLAIN_TEMPLATES).execute(context);

        assertEquals(1, context.getDesignDocument().pages().get(0).components().size());
        assertEquals(0, context.getGraphicsReport().imagesPlaced());
    }

    private static TextBox bodyBox(double y, double h, SourceLink link) {
        return new TextBox("cmp-1", ComponentRole.ARTICLE_BODY, new Frame(20, y, 260, h), 0, false,
                link, "Body", "NPS reached 72 this quarter across 120 accounts.");
    }

    private static DesignDocument documentOf(Page page) {
        return new DesignDocument(1, 1, new DesignMeta("Issue", "job-1"), THEME, List.of(), List.of(page));
    }

    private static DocumentModel documentModelWith(String filename, ImageBlock... images) {
        DocumentMetadata metadata = new DocumentMetadata(filename, DocumentType.DOCX, "jobs/job-1/inputs/" + filename,
                1024, Instant.now());
        return new DocumentModel(metadata, List.of(images));
    }

    private static ContentItem contentItem(SourceRef... sources) {
        return new ContentItem("Delivery milestone", "Summary", BusinessCategory.DELIVERY_HIGHLIGHTS,
                ItemType.METRIC, List.of("NPS: 72"), List.of(sources));
    }

    private static GeneratedNewsletter newsletterWith(ContentItem item) {
        PlannedItem planned = new PlannedItem(item, 9, "strong quarter");
        GeneratedArticle article = new GeneratedArticle("NPS climbs to 72",
                "NPS reached 72 this quarter across 120 accounts.", planned);
        return new GeneratedNewsletter("TD Monthly â€” July 2026",
                List.of(new GeneratedSection(NewsletterSection.DELIVERY_HIGHLIGHTS, List.of(article))));
    }

    private static PipelineContext contextWith(DesignDocument document, GeneratedNewsletter newsletter,
            List<DocumentModel> documents) {
        PipelineContext context = new PipelineContext("job-1", List.of());
        context.setGeneratedNewsletter(newsletter);
        context.setDesignDocument(document);
        documents.forEach(context::addDocument);
        return context;
    }

    private static byte[] tinyPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private record FakeStorageService(Map<String, byte[]> files, Map<String, List<String>> dirs)
            implements StorageService {
        @Override
        public String store(String relativePath, byte[] content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] retrieve(String ref) {
            byte[] bytes = files.get(ref);
            if (bytes == null) {
                throw new IllegalStateException("No content for ref " + ref);
            }
            return bytes;
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
            return dirs.getOrDefault(relativeDir, List.of());
        }
    }
}
