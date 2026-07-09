package com.tcs.contentGenerator.design;

/**
 * Shared color math for deterministic design decisions (dark-vs-light logo
 * variant, on-band text style). Same WCAG relative-luminance formula the
 * review agent's contrast lint uses.
 */
public final class Colors {

    private Colors() {
    }

    /**
     * WCAG relative luminance below 0.5. A missing/unparseable color counts as
     * light — the safe pre-dark-template behavior.
     */
    public static boolean isDark(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            return false;
        }
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = channel((rgb >> 16 & 0xFF) / 255.0);
        double g = channel((rgb >> 8 & 0xFF) / 255.0);
        double b = channel((rgb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b < 0.5;
    }

    private static double channel(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
