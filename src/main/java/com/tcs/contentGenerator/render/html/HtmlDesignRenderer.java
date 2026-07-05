package com.tcs.contentGenerator.render.html;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

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

/**
 * Renders a {@link DesignDocument} as a standalone HTML page: one fixed-size,
 * absolutely-positioned {@code div} per page, matching the design geometry
 * exactly since CSS's {@code pt} unit is the same point our model uses.
 * Deliberately print/preview-style, not responsive — this proves the Design
 * Model visually (Phase A exit criterion); it is not the editor.
 */
@Component
public class HtmlDesignRenderer implements DesignRenderer {

    private static final TextStyle DEFAULT_STYLE = new TextStyle("SansSerif", 10, "normal", "text", 14);

    @Override
    public ExportFormat format() {
        return ExportFormat.HTML;
    }

    @Override
    public byte[] render(DesignDocument document) {
        return buildHtml(document).getBytes(StandardCharsets.UTF_8);
    }

    private String buildHtml(DesignDocument document) {
        Theme theme = document.theme();
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>")
                .append(escape(document.meta().issueTitle()))
                .append("</title><style>")
                .append("body{margin:0;background:#e5e7eb;font-family:sans-serif;}")
                .append(".page{position:relative;background:").append(colorOf(theme, "background", "#fff"))
                .append(";width:").append(pt(theme.pageSize().widthPt()))
                .append(";height:").append(pt(theme.pageSize().heightPt()))
                .append(";margin:16px auto;box-shadow:0 1px 4px rgba(0,0,0,.3);overflow:hidden;}")
                .append(".cmp{position:absolute;box-sizing:border-box;}")
                .append("</style></head><body>");
        for (Page page : document.pages()) {
            html.append("<div class=\"page\">");
            for (var component : page.components()) {
                switch (component) {
                    case TextBox t -> renderText(html, t, theme);
                    case ShapeBox s -> renderShape(html, s, theme);
                    case ImageBox i -> renderImage(html, i, theme);
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
                + "font-family:" + style.fontFamily() + ";font-size:" + style.fontSizePt() + "pt;"
                + "font-weight:" + style.fontWeight() + ";color:" + colorOf(theme, style.colorRole(), "#000") + ";"
                + "line-height:" + style.lineHeightPt() + "pt;white-space:pre-wrap;overflow:hidden;";
        html.append("<div class=\"cmp\" style=\"").append(css).append("\">")
                .append(escape(box.text())).append("</div>");
    }

    private void renderShape(StringBuilder html, ShapeBox box, Theme theme) {
        String css = frameCss(box.frame()) + "background:" + colorOf(theme, box.fillColorRole(), "#ccc") + ";"
                + ("circle".equals(box.shapeType()) ? "border-radius:50%;" : "");
        html.append("<div class=\"cmp\" style=\"").append(css).append("\"></div>");
    }

    private void renderImage(StringBuilder html, ImageBox box, Theme theme) {
        String css = frameCss(box.frame())
                + "border:1px dashed " + colorOf(theme, "divider", "#ccc") + ";"
                + "background:" + colorOf(theme, "surface", "#f4f4f4") + ";"
                + "display:flex;align-items:center;justify-content:center;"
                + "color:" + colorOf(theme, "muted", "#888") + ";font:9pt sans-serif;text-align:center;";
        String label = box.altText() == null || box.altText().isBlank() ? "Image" : box.altText();
        html.append("<div class=\"cmp\" style=\"").append(css).append("\">")
                .append(escape(label)).append("</div>");
    }

    private static String frameCss(Frame frame) {
        return "left:" + pt(frame.x()) + ";top:" + pt(frame.y())
                + ";width:" + pt(frame.w()) + ";height:" + pt(frame.h()) + ";";
    }

    private static String pt(double value) {
        return value + "pt";
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
