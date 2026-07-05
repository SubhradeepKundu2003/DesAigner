package com.tcs.contentGenerator.design;

/**
 * A named text style: font, size, weight, line height (all in points), and a
 * {@code colorRole} naming a color in the {@link Theme} rather than a literal
 * value.
 */
public record TextStyle(String fontFamily, double fontSizePt, String fontWeight,
        String colorRole, double lineHeightPt) {
}
