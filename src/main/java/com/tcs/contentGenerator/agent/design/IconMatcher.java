package com.tcs.contentGenerator.agent.design;

import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * Deterministically maps a newsletter section to its icon. Real icon files
 * live at {@code storage/assets/ICONS/<SECTION_NAME>.svg|png} (same
 * "editors drop files, no code change" convention as {@code AssetLibrary});
 * when a section has one, it is attached as an {@code Asset} under
 * {@link #assetIdFor} and placed as an {@code ImageBox}. The theme color
 * role remains the fallback for sections without an icon file — a small
 * colored dot, exactly the pre-icon behavior.
 */
public final class IconMatcher {

    private IconMatcher() {
    }

    /** Well-known asset id a section's icon is attached under when its file exists. */
    public static String assetIdFor(NewsletterSection section) {
        return "icon-" + section.name();
    }

    public static String colorRoleFor(NewsletterSection section) {
        return switch (section) {
            case LEADERSHIP_MESSAGE, DELIVERY_HIGHLIGHTS, TRAINING_AND_LEARNING -> "primary";
            case PROJECT_UPDATES, CUSTOMER_SUCCESS, UPCOMING_EVENTS -> "secondary";
            case INNOVATION_SPOTLIGHT, AWARDS_AND_RECOGNITION -> "accent";
            case IN_OTHER_NEWS -> "muted";
        };
    }
}
