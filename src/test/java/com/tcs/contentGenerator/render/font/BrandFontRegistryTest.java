package com.tcs.contentGenerator.render.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * No {@code fonts/houschka-rounded-*.ttf} files exist in this repo yet (by
 * design — see TASKS.md / the TCS brand template plan), so constructing the
 * real registry against the real classpath exercises the actual, current
 * no-op path: nothing to register, nothing to embed, every renderer falls
 * back to its existing logical-font handling. Once the real files are added,
 * this test's assertions flip to "present" and should be updated then.
 */
class BrandFontRegistryTest {

    @Test
    void noBundledFontFilesMeansBothWeightsAreAbsent() {
        BrandFontRegistry registry = new BrandFontRegistry();

        assertTrue(registry.bytesFor("normal").isEmpty());
        assertTrue(registry.bytesFor("bold").isEmpty());
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
