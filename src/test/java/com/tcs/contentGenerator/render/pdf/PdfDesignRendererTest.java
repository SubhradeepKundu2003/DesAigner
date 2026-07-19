package com.tcs.contentGenerator.render.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.ShapeBox;
import com.tcs.contentGenerator.design.SourceLink;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.render.ExportFormat;
import com.tcs.contentGenerator.render.font.BrandFontRegistry;
import com.tcs.contentGenerator.render.html.HtmlDesignRenderer;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Verifies the PDF renderer the same way {@code PptxDesignRendererTest} does
 * its format: the rendered bytes are parsed back (with PDFBox) and the
 * assertions run against actual pages and extracted text, not just non-empty
 * output.
 */
class PdfDesignRendererTest {

    private static final Theme THEME = new Theme(
            new PageSize(595, 842),
            Map.of("primary", "#0B5FFF", "surface", "#F4F4F4", "divider", "#D8DEE8",
                    "muted", "#5B6470", "text", "#1B1E23", "background", "#FFFFFF"),
            Map.of("Headline", new TextStyle("SansSerif", 13, "bold", "primary", 17),
                    "Body", new TextStyle("SansSerif", 10, "normal", "text", 14)),
            new Spacing(48, 16));

    private final PdfDesignRenderer renderer = new PdfDesignRenderer(
            new HtmlDesignRenderer(new NoopStorageService(), new BrandFontRegistry()), new BrandFontRegistry());

    @Test
    void reportsPdfFormat() {
        assertEquals(ExportFormat.PDF, renderer.format());
    }

    @Test
    void rendersOnePageWithThemePageSizeAndAllText() throws Exception {
        SourceLink source = new SourceLink("Delivery Highlights", "NPS climbs to 72");
        TextBox headline = new TextBox("cmp-1", ComponentRole.ARTICLE_HEADLINE,
                new Frame(48, 48, 300, 20), 0, false, source, "Headline", "NPS climbs to 72");
        TextBox body = new TextBox("cmp-2", ComponentRole.ARTICLE_BODY,
                new Frame(48, 80, 480, 60), 1, false, source, "Body",
                "Customer satisfaction reached a new high.\nThe survey covered 120 accounts.");
        ShapeBox icon = new ShapeBox("cmp-3", ComponentRole.SECTION_ICON,
                new Frame(48, 150, 12, 12), 2, false, null, "circle", "primary");
        ImageBox placeholder = new ImageBox("cmp-4", ComponentRole.IMAGE_PLACEHOLDER,
                new Frame(48, 170, 200, 120), 3, false, null, null, "Team photo");

        DesignDocument document = new DesignDocument(1, 1,
                new DesignMeta("TD Monthly — July 2026", "job-1"), THEME, List.of(),
                List.of(new Page("page-1", List.of(headline, body, icon, placeholder))));

        byte[] pdfBytes = renderer.render(document);

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, pdf.getNumberOfPages());
            PDRectangle mediaBox = pdf.getPage(0).getMediaBox();
            assertEquals(595, mediaBox.getWidth(), 0.5);
            assertEquals(842, mediaBox.getHeight(), 0.5);

            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("NPS climbs to 72"), "headline missing: " + text);
            assertTrue(text.contains("Customer satisfaction reached a new high."), "body line 1 missing: " + text);
            assertTrue(text.contains("The survey covered 120 accounts."), "body line 2 missing: " + text);
            assertTrue(text.contains("Team photo"), "image placeholder label missing: " + text);
        }
    }

    @Test
    void rendersEachDesignPageAsExactlyOnePdfPage() throws Exception {
        TextBox first = new TextBox("cmp-1", ComponentRole.ARTICLE_HEADLINE,
                new Frame(48, 48, 300, 20), 0, false, null, "Headline", "First page story");
        TextBox second = new TextBox("cmp-2", ComponentRole.ARTICLE_HEADLINE,
                new Frame(48, 48, 300, 20), 0, false, null, "Headline", "Second page story");

        DesignDocument document = new DesignDocument(1, 1,
                new DesignMeta("Issue", "job-1"), THEME, List.of(),
                List.of(new Page("page-1", List.of(first)), new Page("page-2", List.of(second))));

        byte[] pdfBytes = renderer.render(document);

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            // Exactly two: a trailing blank page would mean the page-break CSS regressed.
            assertEquals(2, pdf.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(pdf).contains("Second page story"));

            PDFTextStripper pageOne = new PDFTextStripper();
            pageOne.setStartPage(1);
            pageOne.setEndPage(1);
            assertTrue(pageOne.getText(pdf).contains("First page story"));

            PDFTextStripper pageTwo = new PDFTextStripper();
            pageTwo.setStartPage(2);
            pageTwo.setEndPage(2);
            assertTrue(pageTwo.getText(pdf).contains("Second page story"));
        }
    }

    @Test
    void embedsSvgAssetsViaTheBatikSvgDrawer() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\">"
                + "<rect width=\"40\" height=\"40\" fill=\"#000000\"/></svg>";
        Asset asset = new Asset("brand-logo", "image", "assets/BRAND/logo_black.svg", null, null);
        ImageBox logo = new ImageBox("cmp-1", ComponentRole.LOGO,
                new Frame(48, 48, 40, 40), 0, true, null, "brand-logo", "Company logo");
        DesignDocument document = new DesignDocument(1, 1, new DesignMeta("Issue", "job-1"), THEME,
                List.of(asset), List.of(new Page("page-1", List.of(logo))));
        PdfDesignRenderer svgRenderer = new PdfDesignRenderer(
                new HtmlDesignRenderer(
                        new FixedStorageService(Map.of("assets/BRAND/logo_black.svg",
                                svg.getBytes(StandardCharsets.UTF_8))),
                        new BrandFontRegistry()),
                new BrandFontRegistry());

        byte[] pdfBytes = svgRenderer.render(document);

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, pdf.getNumberOfPages());
            // Not just "didn't throw" — confirm the SVG was actually rasterized and
            // embedded as a real image, not silently dropped or left as text.
            assertFalse(new PDFTextStripper().getText(pdf).contains("Company logo"),
                    "a real embedded image means the placeholder label must not appear");
            // Batik draws the SVG as vector content into a PDFormXObject rather than a
            // raster image — either way, its presence means the SVG was actually
            // rendered, not silently skipped (the no-asset placeholder path draws no
            // XObject at all, just text — already ruled out above).
            PDResources resources = pdf.getPage(0).getResources();
            boolean hasFormXObject = false;
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDFormXObject) {
                    hasFormXObject = true;
                }
            }
            assertTrue(hasFormXObject, "expected the SVG logo to be drawn via a form XObject");
        }
    }

    /**
     * Real bug this test caught: {@code altText} carries free LLM-generated
     * text (article headlines, section titles — see {@code ImagePlacer}) into
     * an {@code alt="..."} HTML attribute. A headline containing a literal
     * quotation mark (small models return these routinely, e.g. a quoted
     * phrase mid-headline) used to break openhtmltopdf's strict XML parse and
     * fail the whole PDF export — while the lenient browser preview and the
     * plain-text PPTX renderer both kept working, so the failure looked
     * random/platform-dependent rather than content-triggered.
     */
    @Test
    void altTextContainingAQuoteStillRendersAValidPdf() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\">"
                + "<rect width=\"40\" height=\"40\" fill=\"#000000\"/></svg>";
        Asset asset = new Asset("photo-1", "image", "assets/x.svg", null, null);
        ImageBox photo = new ImageBox("cmp-1", ComponentRole.IMAGE_PLACEHOLDER,
                new Frame(48, 48, 40, 40), 0, false, null, "photo-1", "Team says \"great quarter\"");
        DesignDocument document = new DesignDocument(1, 1, new DesignMeta("Issue", "job-1"), THEME,
                List.of(asset), List.of(new Page("page-1", List.of(photo))));
        PdfDesignRenderer svgRenderer = new PdfDesignRenderer(
                new HtmlDesignRenderer(
                        new FixedStorageService(Map.of("assets/x.svg", svg.getBytes(StandardCharsets.UTF_8))),
                        new BrandFontRegistry()),
                new BrandFontRegistry());

        byte[] pdfBytes = svgRenderer.render(document);

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }

    /** No fixture here uses a real image asset, so every method is unreachable. */
    private static final class NoopStorageService implements StorageService {
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
            throw new UnsupportedOperationException();
        }
    }

    private record FixedStorageService(Map<String, byte[]> content) implements StorageService {
        @Override
        public String store(String relativePath, byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] retrieve(String ref) {
            byte[] bytes = content.get(ref);
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
            throw new UnsupportedOperationException();
        }
    }
}
