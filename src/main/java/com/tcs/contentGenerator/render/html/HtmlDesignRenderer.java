package com.tcs.contentGenerator.render.html;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.ShapeBox;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.render.DesignRenderer;
import com.tcs.contentGenerator.render.ExportFormat;
import com.tcs.contentGenerator.render.font.BrandFontRegistry;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Renders a {@link DesignDocument} as a standalone HTML page: one fixed-size,
 * absolutely-positioned {@code div} per page, matching the design geometry
 * exactly since CSS's {@code pt} unit is the same point our model uses.
 * Deliberately print/preview-style, not responsive — this proves the Design
 * Model visually (Phase A exit criterion); it is not the editor.
 */
@Component
public class HtmlDesignRenderer implements DesignRenderer {

    private static final Logger log = LoggerFactory.getLogger(HtmlDesignRenderer.class);
    private static final TextStyle DEFAULT_STYLE = new TextStyle("SansSerif", 10, "normal", "text", 14);

    private final StorageService storageService;
    private final BrandFontRegistry fontRegistry;

    public HtmlDesignRenderer(StorageService storageService, BrandFontRegistry fontRegistry) {
        this.storageService = storageService;
        this.fontRegistry = fontRegistry;
    }

    @Override
    public ExportFormat format() {
        return ExportFormat.HTML;
    }

    @Override
    public byte[] render(DesignDocument document) {
        return renderHtml(document, "").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Same document as {@link #render}, as a string, with {@code extraCss}
     * appended after the base rules (so it wins on equal specificity). The
     * output is well-formed XHTML on purpose: the PDF renderer feeds it to
     * openhtmltopdf's XML parser, which rejects plain-HTML void tags.
     */
    public String renderHtml(DesignDocument document, String extraCss) {
        Theme theme = document.theme();
        Map<String, Asset> assetsById = document.assets().stream()
                .collect(Collectors.toMap(Asset::id, Function.identity()));
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><title>")
                .append(escape(document.meta().issueTitle()))
                .append("</title><style>")
                .append("body{margin:0;background:#e5e7eb;font-family:sans-serif;}")
                .append(".page{position:relative;background:").append(colorOf(theme, "background", "#fff"))
                .append(";width:").append(pt(theme.pageSize().widthPt()))
                .append(";height:").append(pt(theme.pageSize().heightPt()))
                .append(";margin:16px auto;box-shadow:0 1px 4px rgba(0,0,0,.3);overflow:hidden;}")
                .append(".cmp{position:absolute;box-sizing:border-box;}")
                .append(fontFaceRules())
                .append(extraCss)
                .append("</style></head><body>");
        for (Page page : document.pages()) {
            html.append("<div class=\"page\">");
            for (var component : page.components()) {
                switch (component) {
                    case TextBox t -> renderText(html, t, theme);
                    case ShapeBox s -> renderShape(html, s, theme);
                    case ImageBox i -> renderImage(html, i, theme, assetsById);
                }
            }
            html.append("</div>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private void renderText(StringBuilder html, TextBox box, Theme theme) {
        TextStyle style = theme.textStyles().getOrDefault(box.styleRef(), DEFAULT_STYLE);
        String css = frameCss(box.frame())
                + "font-family:" + fontFamilyCss(style.fontFamily()) + ";font-size:" + style.fontSizePt() + "pt;"
                + "font-weight:" + style.fontWeight() + ";color:" + colorOf(theme, style.colorRole(), "#000") + ";"
                + "line-height:" + style.lineHeightPt() + "pt;white-space:pre-wrap;overflow:hidden;";
        html.append("<div class=\"cmp\" style=\"").append(css).append("\">")
                .append(escape(box.text())).append("</div>");
    }

    private void renderShape(StringBuilder html, ShapeBox box, Theme theme) {
        // Radius as a length, not 50% — same circle in browsers, but percentage
        // radii are unreliable in openhtmltopdf, which renders this markup too.
        double radius = Math.min(box.frame().w(), box.frame().h()) / 2;
        String css = frameCss(box.frame()) + "background:" + colorOf(theme, box.fillColorRole(), "#ccc") + ";"
                + ("circle".equals(box.shapeType()) ? "border-radius:" + pt(radius) + ";" : "");
        html.append("<div class=\"cmp\" style=\"").append(css).append("\"></div>");
    }

    private void renderImage(StringBuilder html, ImageBox box, Theme theme, Map<String, Asset> assetsById) {
        Asset asset = box.assetId() == null ? null : assetsById.get(box.assetId());
        if (asset != null) {
            String dataUri = dataUriOf(asset);
            if (dataUri != null) {
                html.append("<img class=\"cmp\" style=\"").append(frameCss(box.frame()))
                        .append("object-fit:cover;\" src=\"").append(dataUri).append("\" alt=\"")
                        .append(escape(box.altText() == null ? "" : box.altText())).append("\"/>");
                return;
            }
        }
        renderPlaceholder(html, box, theme);
    }

    private void renderPlaceholder(StringBuilder html, ImageBox box, Theme theme) {
        String css = frameCss(box.frame())
                + "border:1px dashed " + colorOf(theme, "divider", "#ccc") + ";"
                + "background:" + colorOf(theme, "surface", "#f4f4f4") + ";"
                + "display:flex;align-items:center;justify-content:center;"
                + "color:" + colorOf(theme, "muted", "#888") + ";font:9pt sans-serif;text-align:center;";
        String label = box.altText() == null || box.altText().isBlank() ? "Image" : box.altText();
        html.append("<div class=\"cmp\" style=\"").append(css).append("\">")
                .append(escape(label)).append("</div>");
    }

    private String dataUriOf(Asset asset) {
        try {
            byte[] bytes = storageService.retrieve(asset.storedRef());
            String mime = mimeTypeOf(asset.storedRef());
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (RuntimeException e) {
            log.warn("Could not embed asset {} ({}) for HTML preview; using placeholder",
                    asset.id(), asset.storedRef(), e);
            return null;
        }
    }

    private static String mimeTypeOf(String ref) {
        String lower = ref == null ? "" : ref.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "image/png";
    }

    private static String frameCss(Frame frame) {
        return "left:" + pt(frame.x()) + ";top:" + pt(frame.y())
                + ";width:" + pt(frame.w()) + ";height:" + pt(frame.h()) + ";";
    }

    private static String pt(double value) {
        return value + "pt";
    }

    /**
     * Theme font names are logical families ("SansSerif", "Serif") that neither
     * browsers nor openhtmltopdf know — append the matching CSS generic family
     * so every renderer falls back to a font of the right shape instead of its
     * own arbitrary default.
     */
    private static String fontFamilyCss(String family) {
        String lower = family.toLowerCase();
        String generic = lower.contains("mono") ? "monospace"
                : lower.contains("serif") && !lower.contains("sans") ? "serif"
                : "sans-serif";
        // Single-quoted: this value is inlined into an HTML style="..." attribute
        // (itself double-quoted), so a double-quoted family name here would
        // prematurely close the attribute and break the XHTML the PDF renderer parses.
        return "'" + family + "'," + generic;
    }

    /**
     * Emits an {@code @font-face} rule per brand-font weight available from
     * {@link BrandFontRegistry}, embedding the bytes as a base64 {@code data:}
     * URL (no separate font-serving endpoint needed). Empty when no brand font
     * file has been bundled yet — every consumer already falls back to
     * {@link #fontFamilyCss} in that case.
     */
    private String fontFaceRules() {
        StringBuilder css = new StringBuilder();
        for (String weight : new String[] {"normal", "bold"}) {
            fontRegistry.bytesFor(weight).ifPresent(bytes -> css.append("@font-face{font-family:\"")
                    .append(fontRegistry.family()).append("\";font-weight:").append(weight)
                    .append(";src:url(data:font/ttf;base64,")
                    .append(Base64.getEncoder().encodeToString(bytes)).append(") format('truetype');}"));
        }
        return css.toString();
    }

    private static String colorOf(Theme theme, String role, String fallback) {
        if (role == null) {
            return fallback;
        }
        return theme.colors().getOrDefault(role, fallback);
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
