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
public record Decor(Cover cover, Masthead masthead, SectionHeader sectionHeader, Hero hero,
        SectionBand sectionBand, Photo photo, Cards cards, StatCard statCard, Footer footer) {

    /**
     * Card grid: STANDARD sections with three or more articles lay out as a
     * two-column grid of rounded, softly shadowed cards ({@code fill} names
     * the card surface role). Cards replace side images and the below-slot
     * for those sections; sections with fewer articles keep their configured
     * photo treatment. Null = no card grid.
     */
    public record Cards(String fill) {
    }

    /**
     * Dedicated magazine cover as page 1: full-bleed {@code fill} background,
     * the brand logo (variant picked against the fill), a large rounded photo,
     * a right-aligned display title with its second line in
     * {@code titleAccent}, and a decorative wave-lines band along the bottom.
     * Uses the optional {@code CoverTitle}/{@code CoverTitleAccent}/
     * {@code CoverSubtitle} text styles. Null = no cover (content starts on
     * page 1 as before).
     */
    public record Cover(String fill, String titleAccent) {
    }

    /**
     * Full-bleed gradient band behind the logo + issue title. {@code angle} 0 =
     * left→right, 90 = top→bottom. {@code edge} "wave" curves the bottom edge;
     * anything else is flat. {@code heightPt} is a minimum — the band grows to
     * fit the measured masthead content.
     */
    public record Masthead(String style, String from, String to, int angle, double heightPt, String edge) {
    }

    /**
     * "chip" draws a soft rounded tint behind each section's icon/dot.
     * {@code kicker} additionally replaces the thin inter-section divider with
     * a short accent bar above each header and renders the section title
     * UPPERCASE (in the theme's {@code SectionTitleKicker} style if defined).
     */
    public record SectionHeader(String style, String colorRole, boolean kicker) {
    }

    /**
     * HERO section treatment. {@code style} "photo-led" = magazine style: a
     * full-width photo slot at the top of the section (filled by the graphics
     * agent like any reserved slot), headline and lead below it, no panel.
     * Anything else (including null) = "panel": a rounded tinted panel behind
     * the section with a large translucent quote glyph in the {@code accent}
     * color; {@code fill}/{@code accent} only apply to the panel style.
     */
    public record Hero(String style, String fill, String accent) {
    }

    /**
     * Full-bleed tint band behind every other section — placed as a
     * {@code ShapeBox} (not a baked image) so the review agent's contrast
     * lint can still reason about text on it. Sections that cross a page
     * break skip their band (v1 trade-off).
     */
    public record SectionBand(String fill) {
    }

    /**
     * Photo treatment applied by the graphics agent: crop-to-fill plus
     * {@code clip} "ellipse" / "rounded" (with {@code cornerRadiusPt}) /
     * anything else = straight edges, and an optional soft shadow.
     * {@code placement} "side" lays STANDARD-section articles out with their
     * photo <em>beside</em> the text, alternating right/left across the issue
     * (magazine style); anything else (including null) keeps one photo slot
     * below each eligible section's content.
     */
    public record Photo(String clip, double cornerRadiusPt, boolean shadow, String placement) {
    }

    /** Rounded card with a left accent bar behind the stat value/label row. */
    public record StatCard(String fill, String accent, boolean shadow) {
    }

    /** Thin full-width gradient strip at the bottom of every page. */
    public record Footer(String style, String from, String to) {
    }
}
