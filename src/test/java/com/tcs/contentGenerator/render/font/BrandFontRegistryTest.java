package com.tcs.contentGenerator.render.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The real Houschka Rounded Alt files now live at
 * {@code src/main/resources/fonts/} (Medium = normal weight, DemiBold = bold),
 * so constructing the registry against the real classpath exercises the
 * loaded path: both weights present, registered with AWT, bytes available for
 * the HTML {@code @font-face} and PDF {@code useFont} embedding.
 */
class BrandFontRegistryTest {

    @Test
    void bundledFontFilesLoadBothWeights() {
        BrandFontRegistry registry = new BrandFontRegistry();

        assertTrue(registry.bytesFor("normal").isPresent());
        assertTrue(registry.bytesFor("bold").isPresent());
    }

    @Test
    void weightsAreDistinctFiles() {
        BrandFontRegistry registry = new BrandFontRegistry();

        assertTrue(registry.bytesFor("normal").get().length != registry.bytesFor("bold").get().length,
                "normal and bold should come from different font files");
    }

    @Test
    void familyNameMatchesTheThemeJsonFontFamily() {
        assertEquals("Houschka Rounded", new BrandFontRegistry().family());
    }

    @Test
    void bytesForTreatsAnyNonBoldWeightAsNormal() {
        BrandFontRegistry registry = new BrandFontRegistry();

        assertEquals(registry.bytesFor("normal"), registry.bytesFor("anything-else"));
        assertEquals(registry.bytesFor("normal"), registry.bytesFor(null));
    }
}
