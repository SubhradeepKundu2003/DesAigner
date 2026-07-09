package com.tcs.contentGenerator.design;

/**
 * The semantic purpose of a {@link Component}, independent of how it is drawn.
 * The editor and renderers use this to know what a box <em>means</em>, not just
 * where it sits.
 */
public enum ComponentRole {
    ISSUE_TITLE,
    SECTION_TITLE,
    SECTION_ICON,
    DIVIDER,
    ARTICLE_HEADLINE,
    /** Larger, muted first paragraph of an article (editorial lead) — split off by the layout engine. */
    ARTICLE_LEAD,
    ARTICLE_BODY,
    STAT_VALUE,
    STAT_LABEL,
    IMAGE_PLACEHOLDER,
    LOGO,
    /**
     * Purely decorative element (masthead band, section chip, stat card,
     * footer band) baked as an image asset. Deliberately allowed to bleed to
     * the page edges and sit behind content — the review agent's margin and
     * overlap lints exempt this role.
     */
    DECORATION
}
