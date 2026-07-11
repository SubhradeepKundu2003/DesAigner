package com.tcs.contentGenerator.agent.design.infographic;

import java.util.Locale;

import com.tcs.contentGenerator.design.Colors;
import com.tcs.contentGenerator.design.Theme;

/**
 * Draws infographic <em>shapes</em> as SVG strings — the infographic sibling
 * of {@code DecorPainter}: pure functions of (params, theme, frame size), no
 * I/O, text slots left empty because the real text is placed as TextBoxes on
 * top by {@code LayoutEngine}. Only the display <em>number</em> is painted
 * here (it is decoration, not content).
 *
 * <p>Because decoration assets are regenerated from the asset id alone (see
 * {@code DesignCompositionAgent.decorAssets}), every drawing parameter must
 * ride inside the id — {@link #encode} packs {@code kind.role.role.index}
 * dot-separated (roles and kinds never contain dots or dashes), the same
 * trick {@code decor-sectiontint-<role>-<id>} already uses for one role.
 */
public final class InfographicPainter {

    /** Diameter of the numbered lead disc — layout and painter must agree. */
    public static final double DISC = 30;
    /** Gap between the disc and where the bar's text begins. */
    public static final double DISC_GAP = 12;
    /** Inner padding of a bar's text area. */
    public static final double BAR_PADDING = 10;
    /** How far the bar slides left under the disc (the disc overlaps it). */
    private static final double BAR_OVERLAP = 10;
    /** Card badge size + padding for the {@code CARD_GRID} archetype. */
    public static final double CARD_BADGE = 26;
    public static final double CARD_PADDING = 12;
    /** Node diameter for the {@code TIMELINE} archetype's central connector. */
    public static final double TIMELINE_NODE = 24;
    private static final String SHADOW_FILTER = """
            <defs><filter id="s" x="-20%" y="-20%" width="140%" height="160%">\
            <feGaussianBlur in="SourceAlpha" stdDeviation="3"/>\
            <feOffset dx="0" dy="2"/>\
            <feComponentTransfer><feFuncA type="linear" slope="0.3"/></feComponentTransfer>\
            <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>\
            </filter></defs>""";

    private InfographicPainter() {
    }

    /** Packs shape params + 1-based item number into an asset-id-safe token. */
    public static String encode(InfographicSpec.Shape shape, int number) {
        return shape.kind() + "." + shape.barFill() + "." + shape.leadFill() + "." + number;
    }

    /** Dispatches an {@link #encode}d token back to its painter; null for an unknown kind. */
    public static String paint(String params, Theme theme, double w, double h) {
        String[] parts = params.split("\\.");
        String role1 = parts[1];
        String role2 = parts[2];
        int number = Integer.parseInt(parts[3]);
        return switch (parts[0]) {
            case "numberedBars" -> numberedBars(theme, role1, role2, number, w, h);
            case "chevronBars" -> chevronBars(theme, role1, role2, number, w, h);
            case "pointCard" -> pointCard(theme, role1, role2, number, w, h);
            case "timelineNode" -> timelineNode(theme, role1, role2, number, w, h);
            default -> null;
        };
    }

    /**
     * One row of the numbered-list design: a rounded full-height bar (text
     * sits on it) with a numbered disc overlapping its left edge — "01", "02",
     * ... in white or near-black, whichever contrasts with the disc fill.
     */
    public static String numberedBars(Theme theme, String barFillRole, String discFillRole,
            int number, double w, double h) {
        String barFill = color(theme, barFillRole);
        double barX = DISC - BAR_OVERLAP;
        double radius = Math.min(12, h * 0.25);
        String bar = "<rect x=\"%s\" y=\"0\" width=\"%s\" height=\"%s\" rx=\"%s\" fill=\"%s\"/>"
                .formatted(fmt(barX), fmt(w - barX), fmt(h), fmt(radius), barFill);
        return svg(w, h, bar + disc(theme, discFillRole, number, DISC / 2, h / 2, DISC / 2));
    }

    /**
     * The {@code KPI_BARS} sibling of {@link #numberedBars}: same disc-and-bar
     * row, but the bar's right edge comes to an arrow point instead of a
     * rounded corner — the visual cue that this row is one of a ranked/scored
     * set (the reference "chevron" KPI rows), not a plain sequence.
     */
    public static String chevronBars(Theme theme, String barFillRole, String discFillRole,
            int number, double w, double h) {
        String barFill = color(theme, barFillRole);
        double barX = DISC - BAR_OVERLAP;
        double tip = Math.min(18, h * 0.32);
        String polygon = ("<polygon points=\"%s,0 %s,0 %s,%s %s,%s %s,%s\" fill=\"%s\"/>")
                .formatted(fmt(barX), fmt(w - tip), fmt(w), fmt(h / 2), fmt(w - tip), fmt(h),
                        fmt(barX), fmt(h), barFill);
        return svg(w, h, polygon + disc(theme, discFillRole, number, DISC / 2, h / 2, DISC / 2));
    }

    /**
     * One cell of the {@code CARD_GRID} design: a rounded, softly shadowed
     * card with a small numbered badge in its top-left corner — text (label +
     * one-liner) is placed below the badge by {@code LayoutEngine}.
     */
    public static String pointCard(Theme theme, String cardFillRole, String badgeFillRole,
            int number, double w, double h) {
        String cardFill = color(theme, cardFillRole);
        double inset = 4;
        String card = SHADOW_FILTER
                + "<rect x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" rx=\"10\" fill=\"%s\" filter=\"url(#s)\"/>"
                        .formatted(fmt(inset), fmt(inset), fmt(w - 2 * inset), fmt(h - 2 * inset), cardFill);
        double cx = inset + CARD_PADDING + CARD_BADGE / 2;
        double cy = inset + CARD_PADDING + CARD_BADGE / 2;
        return svg(w, h, card + disc(theme, badgeFillRole, number, cx, cy, CARD_BADGE / 2));
    }

    /**
     * One segment of the {@code TIMELINE} design: a vertical connector line
     * down the frame's horizontal center with a numbered node centered on it.
     * Consecutive rows' segments visually chain into one continuous line down
     * the page; the label/one-liner text alternates left/right of the line
     * (the "zigzag" reference look), placed by {@code LayoutEngine}.
     */
    public static String timelineNode(Theme theme, String lineFillRole, String nodeFillRole,
            int number, double w, double h) {
        String lineFill = color(theme, lineFillRole);
        double cx = w / 2;
        String line = "<line x1=\"%s\" y1=\"0\" x2=\"%s\" y2=\"%s\" stroke=\"%s\" stroke-width=\"3\"/>"
                .formatted(fmt(cx), fmt(cx), fmt(h), lineFill);
        return svg(w, h, line + disc(theme, nodeFillRole, number, cx, h / 2, TIMELINE_NODE / 2));
    }

    /** A filled circle with a centered bold two-digit number, contrast-picked against its own fill. */
    private static String disc(Theme theme, String fillRole, int number, double cx, double cy, double r) {
        String fill = color(theme, fillRole);
        String numberColor = Colors.isDark(fill) ? "#FFFFFF" : "#1B1E23";
        double fontSize = r * 0.85;
        return "<circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"%s\"/>"
                        .formatted(fmt(cx), fmt(cy), fmt(r), fill)
                + ("<text x=\"%s\" y=\"%s\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" "
                        + "font-size=\"%s\" font-weight=\"bold\" fill=\"%s\">%02d</text>")
                        .formatted(fmt(cx), fmt(cy + fontSize * 0.35), fmt(fontSize), numberColor, number);
    }

    private static String svg(double w, double h, String content) {
        // integer intrinsic size, sub-pixel viewBox — same Batik constraint as DecorPainter
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %s %s\" "
                + "width=\"%d\" height=\"%d\">%s</svg>")
                .formatted(fmt(w), fmt(h), (int) Math.ceil(w), (int) Math.ceil(h), content);
    }

    private static String color(Theme theme, String role) {
        return theme.colors().getOrDefault(role, "#888888");
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
