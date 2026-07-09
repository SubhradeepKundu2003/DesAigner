package com.tcs.contentGenerator.agent.design;

/**
 * A template's optional decoration vocabulary — the visual layer beyond flat
 * text and solid shapes: gradient masthead band, section-header chips,
 * stat-callout cards, treated photos, footer band. Every color field names a
 * theme color <em>role</em>, never a literal hex, so restyling stays one-file.
 * Any part (or the whole record) may be null — absent decor means exactly the
 * pre-decor plain output ({@code td-classic} ships without one as the
 * regression baseline). Decorations are baked into SVG/PNG assets at
 * composition time ({@code DecorPainter}/{@code PhotoEffects}) because the
 * PPTX writer has no gradient/shadow API and openhtmltopdf has no
 * box-shadow/clip — the renderers stay unchanged.
 */
public record Decor(Masthead masthead, SectionHeader sectionHeader, Photo photo,
        StatCard statCard, Footer footer) {

    /**
     * Full-bleed gradient band behind the logo + issue title. {@code angle} 0 =
     * left→right, 90 = top→bottom. {@code edge} "wave" curves the bottom edge;
     * anything else is flat. {@code heightPt} is a minimum — the band grows to
     * fit the measured masthead content.
     */
    public record Masthead(String style, String from, String to, int angle, double heightPt, String edge) {
    }

    /** "chip" draws a soft rounded tint behind each section's icon/dot. */
    public record SectionHeader(String style, String colorRole) {
    }

    /**
     * Photo treatment applied by the graphics agent: crop-to-fill plus
     * {@code clip} "ellipse" / "rounded" (with {@code cornerRadiusPt}) /
     * anything else = straight edges, and an optional soft shadow.
     */
    public record Photo(String clip, double cornerRadiusPt, boolean shadow) {
    }

    /** Rounded card with a left accent bar behind the stat value/label row. */
    public record StatCard(String fill, String accent, boolean shadow) {
    }

    /** Thin full-width gradient strip at the bottom of every page. */
    public record Footer(String style, String from, String to) {
    }
}
