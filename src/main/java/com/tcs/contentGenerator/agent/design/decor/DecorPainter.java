package com.tcs.contentGenerator.agent.design.decor;

import java.util.Locale;

import com.tcs.contentGenerator.agent.design.Decor;
import com.tcs.contentGenerator.design.Theme;

/**
 * Builds decoration SVGs as strings — pure functions of (spec, theme, frame
 * size), no I/O. SVG is the one format all three renderers already handle
 * identically (HTML data URI, PDF vector via Batik, PPTX raster via Batik's
 * {@code PNGTranscoder}), so decorations render consistently everywhere with
 * zero renderer changes. Shadows use the classic
 * {@code feGaussianBlur}+{@code feOffset}+{@code feMerge} filter chain — the
 * form Batik reliably supports ({@code feDropShadow} is avoided on purpose).
 */
public final class DecorPainter {

    private DecorPainter() {
    }

    /** Gradient masthead band, optionally with a wave bottom edge. */
    public static String masthead(Decor.Masthead spec, Theme theme, double w, double h) {
        String from = color(theme, spec.from());
        String to = color(theme, spec.to());
        String shape = "wave".equals(spec.edge())
                ? "<path d=\"%s\" fill=\"url(#g)\"/>".formatted(wavePath(w, h))
                : "<rect x=\"0\" y=\"0\" width=\"%s\" height=\"%s\" fill=\"url(#g)\"/>".formatted(fmt(w), fmt(h));
        return svg(w, h, gradientDef(from, to, spec.angle()) + shape);
    }

    /** Soft rounded tint behind a section icon/dot. */
    public static String chip(Decor.SectionHeader spec, Theme theme, double w, double h) {
        return svg(w, h,
                "<rect x=\"0\" y=\"0\" width=\"%s\" height=\"%s\" rx=\"%s\" fill=\"%s\" fill-opacity=\"0.16\"/>"
                        .formatted(fmt(w), fmt(h), fmt(Math.min(w, h) * 0.3), color(theme, spec.colorRole())));
    }

    /** Rounded card with a left accent bar behind the stat value/label row. */
    public static String statCard(Decor.StatCard spec, Theme theme, double w, double h) {
        String fill = color(theme, spec.fill());
        String accent = color(theme, spec.accent());
        // inset leaves room for the blur; the card proper is slightly smaller
        double inset = 4;
        double cardW = w - 2 * inset;
        double cardH = h - 2 * inset;
        String filter = spec.shadow() ? SHADOW_FILTER : "";
        String filterRef = spec.shadow() ? " filter=\"url(#s)\"" : "";
        return svg(w, h, filter
                + "<rect x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" rx=\"8\" fill=\"%s\"%s/>"
                        .formatted(fmt(inset), fmt(inset), fmt(cardW), fmt(cardH), fill, filterRef)
                + "<rect x=\"%s\" y=\"%s\" width=\"4\" height=\"%s\" rx=\"2\" fill=\"%s\"/>"
                        .formatted(fmt(inset), fmt(inset), fmt(cardH), accent));
    }

    /** Thin gradient strip along the bottom page edge. */
    public static String footer(Decor.Footer spec, Theme theme, double w, double h) {
        return svg(w, h, gradientDef(color(theme, spec.from()), color(theme, spec.to()), 0)
                + "<rect x=\"0\" y=\"0\" width=\"%s\" height=\"%s\" fill=\"url(#g)\"/>".formatted(fmt(w), fmt(h)));
    }

    private static final String SHADOW_FILTER = """
            <defs><filter id="s" x="-20%" y="-20%" width="140%" height="160%">\
            <feGaussianBlur in="SourceAlpha" stdDeviation="3"/>\
            <feOffset dx="0" dy="2"/>\
            <feComponentTransfer><feFuncA type="linear" slope="0.3"/></feComponentTransfer>\
            <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>\
            </filter></defs>""";

    private static String gradientDef(String from, String to, int angle) {
        // 90 = top->bottom, anything else = left->right; richer angles can come later
        String axis = angle == 90 ? "x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"" : "x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\"";
        return ("<defs><linearGradient id=\"g\" " + axis + ">"
                + "<stop offset=\"0\" stop-color=\"%s\"/><stop offset=\"1\" stop-color=\"%s\"/>"
                + "</linearGradient></defs>").formatted(from, to);
    }

    /** Full-width band whose bottom edge is one gentle cubic wave. */
    private static String wavePath(double w, double h) {
        double dip = Math.min(24, h * 0.2);
        return "M0,0 H%s V%s C%s,%s %s,%s 0,%s Z".formatted(
                fmt(w), fmt(h - dip),
                fmt(w * 0.66), fmt(h + dip * 0.3),
                fmt(w * 0.33), fmt(h - dip * 2),
                fmt(h - dip * 0.4));
    }

    private static String svg(double w, double h, String content) {
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %s %s\" "
                + "width=\"%s\" height=\"%s\">%s</svg>")
                .formatted(fmt(w), fmt(h), fmt(w), fmt(h), content);
    }

    private static String color(Theme theme, String role) {
        return theme.colors().getOrDefault(role, "#888888");
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
