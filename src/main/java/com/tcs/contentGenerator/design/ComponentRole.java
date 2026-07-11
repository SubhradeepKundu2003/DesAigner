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
    /** Short label of one infographic point (numbered bar, card, timeline node, ...). */
    INFOGRAPHIC_LABEL,
    /**
     * One-line description of one infographic point. Deliberately not
     * {@link #ARTICLE_BODY}: that role triggers a per-box editorial LLM check
     * and anchors image gap-scavenging — wrong for short point captions.
     */
    INFOGRAPHIC_TEXT,
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
