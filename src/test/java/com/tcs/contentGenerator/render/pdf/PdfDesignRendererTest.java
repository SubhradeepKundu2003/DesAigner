package com.tcs.contentGenerator.render.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

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
import com.tcs.contentGenerator.render.html.HtmlDesignRenderer;

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

    private final PdfDesignRenderer renderer = new PdfDesignRenderer(new HtmlDesignRenderer());

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
}
