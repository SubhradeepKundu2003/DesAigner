package com.tcs.contentGenerator.agent.design;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * Deterministically maps a newsletter section to its icon. Real icon files
 * live at {@code storage/assets/ICONS/<SECTION_NAME>.svg|png} (same
 * "editors drop files, no code change" convention as {@code AssetLibrary});
 * when a section has one, it is attached as an {@code Asset} under
 * {@link #assetIdFor} and placed as an {@code ImageBox}. The theme color
 * role remains the fallback for sections without an icon file — a small
 * colored dot, exactly the pre-icon behavior.
 *
 * <p>The same {@code assets/ICONS/} folder doubles as the pool for
 * infographic <em>per-point</em> icons: any file there whose basename isn't
 * a {@link NewsletterSection} name becomes a keyword ({@link #matchPointIcon}
 * matches point labels against it) instead of a section icon — see
 * {@code DesignCompositionAgent.listPointIcons}.
 */
public final class IconMatcher {

    /** Prefix of {@link #assetIdForPoint}'s well-known ids — {@code DesignCompositionAgent} strips it back off to recover the keyword. */
    public static final String POINT_ICON_ASSET_PREFIX = "icon-point-";

    private IconMatcher() {
    }

    /** Well-known asset id a section's icon is attached under when its file exists. */
    public static String assetIdFor(NewsletterSection section) {
        return "icon-" + section.name();
    }

    /** Well-known asset id a point's icon is attached under, keyed by its matched keyword. */
    public static String assetIdForPoint(String keyword) {
        return POINT_ICON_ASSET_PREFIX + keyword;
    }

    /**
     * The longest {@code keywords} entry that appears as a whole word in the
     * point's label, case-insensitive, or {@code null} if none match.
     * Longest-wins so a more specific keyword (e.g. "leadership" over "lead")
     * is preferred when both happen to be present as icon files. No match
     * means the infographic painter falls back to its numbered disc.
     */
    public static String matchPointIcon(String label, Set<String> keywords) {
        String lower = label.toLowerCase(Locale.ROOT);
        String best = null;
        for (String keyword : keywords) {
            if (!keyword.isBlank() && containsWord(lower, keyword.toLowerCase(Locale.ROOT))
                    && (best == null || keyword.length() > best.length())) {
                best = keyword;
            }
        }
        return best;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
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
