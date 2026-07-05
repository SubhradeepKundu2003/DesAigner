package com.tcs.contentGenerator.render.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.stereotype.Component;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.render.DesignRenderer;
import com.tcs.contentGenerator.render.ExportFormat;
import com.tcs.contentGenerator.render.font.BrandFontRegistry;
import com.tcs.contentGenerator.render.html.HtmlDesignRenderer;

/**
 * Renders a {@link DesignDocument} to PDF by feeding the HTML renderer's
 * output through openhtmltopdf (v1 strategy from §3.10: absolute-positioned
 * XHTML converts reliably; direct PDFBox drawing stays the v2 option if
 * fidelity ever demands it). The only difference from the browser preview is
 * a print stylesheet: the PDF page box matches the theme's page size exactly,
 * and the preview chrome (grey backdrop, gaps between pages, drop shadow) is
 * stripped so each {@code div.page} becomes one full-bleed PDF page.
 */
@Component
public class PdfDesignRenderer implements DesignRenderer {

    private final HtmlDesignRenderer htmlRenderer;
    private final BrandFontRegistry fontRegistry;

    public PdfDesignRenderer(HtmlDesignRenderer htmlRenderer, BrandFontRegistry fontRegistry) {
        this.htmlRenderer = htmlRenderer;
        this.fontRegistry = fontRegistry;
    }

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] render(DesignDocument document) {
        PageSize size = document.theme().pageSize();
        String printCss = "@page{size:" + size.widthPt() + "pt " + size.heightPt() + "pt;margin:0;}"
                + "body{background:#fff;}"
                + ".page{margin:0;box-shadow:none;page-break-before:always;}"
                + ".page:first-child{page-break-before:auto;}";
        String xhtml = htmlRenderer.renderHtml(document, printCss);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                BatikSVGDrawer svgDrawer = new BatikSVGDrawer()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useSVGDrawer(svgDrawer);
            fontRegistry.bytesFor("normal").ifPresent(bytes -> builder.useFont(
                    () -> new ByteArrayInputStream(bytes), fontRegistry.family(), 400, FontStyle.NORMAL, true));
            fontRegistry.bytesFor("bold").ifPresent(bytes -> builder.useFont(
                    () -> new ByteArrayInputStream(bytes), fontRegistry.family(), 700, FontStyle.NORMAL, true));
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF rendering failed for job "
                    + document.meta().jobId(), e);
        }
    }
}
