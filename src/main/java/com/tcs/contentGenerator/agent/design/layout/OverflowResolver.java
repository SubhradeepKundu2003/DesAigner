package com.tcs.contentGenerator.agent.design.layout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clamps a component's natural height to what a single page can ever hold.
 * Fixed geometry means there is no automatic reflow after this point — a
 * clamped component will visually overflow its frame until a human edits it
 * in the future editor. That is an accepted v1 trade-off, not a bug; this
 * class exists so the trade-off happens in exactly one place and is logged.
 */
public final class OverflowResolver {

    private static final Logger log = LoggerFactory.getLogger(OverflowResolver.class);

    private OverflowResolver() {
    }

    public static double clamp(double desiredHeightPt, double maxPageContentHeightPt, String label) {
        if (desiredHeightPt <= maxPageContentHeightPt) {
            return desiredHeightPt;
        }
        log.warn("{} needs {}pt but a page only has {}pt of content height; clamping — "
                + "content will overflow its frame until edited",
                label, Math.round(desiredHeightPt), Math.round(maxPageContentHeightPt));
        return maxPageContentHeightPt;
    }
}
