package com.tcs.contentGenerator.agent.design;

import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * Deterministically maps a newsletter section to a theme color role, used to
 * draw a small colored section-icon dot. A stand-in for real iconography —
 * Agent #8 (graphics) sources real photos for articles, but section icons
 * still use this dot; swapping in real icons later is a renderer change, not
 * a composition change.
 */
public final class IconMatcher {

    private IconMatcher() {
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
