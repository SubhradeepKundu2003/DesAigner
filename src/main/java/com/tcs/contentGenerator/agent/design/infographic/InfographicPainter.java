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

    private InfographicPainter() {
    }

    /** Packs shape params + 1-based item number into an asset-id-safe token. */
    public static String encode(InfographicSpec.Shape shape, int number) {
        return shape.kind() + "." + shape.barFill() + "." + shape.leadFill() + "." + number;
    }

    /** Dispatches an {@link #encode}d token back to its painter; null for an unknown kind. */
    public static String paint(String params, Theme theme, double w, double h) {
        String[] parts = params.split("\\.");
        return switch (parts[0]) {
            case "numberedBars" -> numberedBars(theme, parts[1], parts[2], Integer.parseInt(parts[3]), w, h);
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
        String discFill = color(theme, discFillRole);
        String numberColor = Colors.isDark(discFill) ? "#FFFFFF" : "#1B1E23";
        double barX = DISC - BAR_OVERLAP;
        double radius = Math.min(12, h * 0.25);
        double fontSize = DISC * 0.42;
        return svg(w, h,
                "<rect x=\"%s\" y=\"0\" width=\"%s\" height=\"%s\" rx=\"%s\" fill=\"%s\"/>"
                        .formatted(fmt(barX), fmt(w - barX), fmt(h), fmt(radius), barFill)
                + "<circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"%s\"/>"
                        .formatted(fmt(DISC / 2), fmt(h / 2), fmt(DISC / 2), discFill)
                + ("<text x=\"%s\" y=\"%s\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" "
                        + "font-size=\"%s\" font-weight=\"bold\" fill=\"%s\">%02d</text>")
                        .formatted(fmt(DISC / 2), fmt(h / 2 + fontSize * 0.35), fmt(fontSize),
                                numberColor, number));
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
