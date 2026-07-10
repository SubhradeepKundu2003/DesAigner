package com.tcs.contentGenerator.design;

/**
 * A named text style: font, size, weight, line height (all in points), a
 * {@code colorRole} naming a color in the {@link Theme} rather than a literal
 * value, and an optional {@code align} ("right"/"center"; null = left) used by
 * cover/display typography.
 */
public record TextStyle(String fontFamily, double fontSizePt, String fontWeight,
        String colorRole, double lineHeightPt, String align) {

    /** Left-aligned style — the overwhelmingly common case. */
    public TextStyle(String fontFamily, double fontSizePt, String fontWeight,
            String colorRole, double lineHeightPt) {
        this(fontFamily, fontSizePt, fontWeight, colorRole, lineHeightPt, null);
    }
}
