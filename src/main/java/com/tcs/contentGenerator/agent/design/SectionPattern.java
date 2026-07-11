package com.tcs.contentGenerator.agent.design;

/**
 * The design pattern a section's article(s) are laid out with. Chosen by
 * {@link DesignCompositionAgent} (semantic, deterministic); the geometry for
 * each is produced by {@code layout.LayoutEngine}.
 */
public enum SectionPattern {
    /** One lead article, full width, given the biggest headline on the page. */
    HERO,
    /** Each article stacked full-width: headline then body. */
    STANDARD,
    /** Two articles side by side in equal columns. */
    TWO_COLUMN,
    /** A single article with a key metric pulled out into a large stat + label. */
    STAT_CALLOUT,
    /**
     * A single article carrying two or more numeric key metrics, pulled out into
     * a horizontal row of KPI tiles (big value + label each) above the body —
     * an infographic extension of {@link #STAT_CALLOUT}.
     */
    KPI_TILES,
    /** Compact one-line-per-item list, for sections like Upcoming Events. */
    EVENT_LIST,
    /**
     * A single article whose enumerable points (steps, pillars, categories —
     * from the generation agent's {@code POINT:} protocol) are rendered as a
     * branded infographic chosen from the {@code infographics/} spec catalog
     * by {@code InfographicSelector} — body prose stays below the graphic.
     */
    INFOGRAPHIC
}
