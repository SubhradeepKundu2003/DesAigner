package com.tcs.contentGenerator.design;

import java.util.Map;

/**
 * The visual identity of an issue: page geometry, named colors, named text
 * styles, and spacing. Components reference styles/colors by name
 * ({@code styleRef}, {@code colorRole}) so restyling the theme restyles the
 * whole document.
 */
public record Theme(PageSize pageSize, Map<String, String> colors,
        Map<String, TextStyle> textStyles, Spacing spacing) {

    public Theme {
        colors = colors == null ? Map.of() : Map.copyOf(colors);
        textStyles = textStyles == null ? Map.of() : Map.copyOf(textStyles);
    }
}
