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
    ARTICLE_BODY,
    STAT_VALUE,
    STAT_LABEL,
    IMAGE_PLACEHOLDER,
    LOGO
}
