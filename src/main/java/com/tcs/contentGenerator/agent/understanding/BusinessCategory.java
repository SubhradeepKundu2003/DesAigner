package com.tcs.contentGenerator.agent.understanding;

/**
 * The business categories a {@link ContentItem} can be classified into. These map
 * to the newsletter's recurring sections. {@link #OTHER} is the fallback for items
 * that don't fit a known bucket (and for lenient parsing of model output).
 */
public enum BusinessCategory {

    PROJECT_UPDATES("Project Updates"),
    AWARDS_AND_RECOGNITION("Awards & Recognition"),
    TRAINING_AND_LEARNING("Training & Learning"),
    DELIVERY_HIGHLIGHTS("Delivery Highlights"),
    CUSTOMER_SUCCESS("Customer Success"),
    TECHNOLOGY_INITIATIVES("Technology Initiatives"),
    EVENTS("Events"),
    OTHER("Other");

    private final String label;

    BusinessCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Lenient parse of a model-supplied category. Matches on enum name or display
     * label, ignoring case, spaces, and separators; unknown values fall back to
     * {@link #OTHER} so a stray classification never breaks the pipeline.
     */
    public static BusinessCategory fromLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String normalized = normalize(raw);
        for (BusinessCategory category : values()) {
            if (normalize(category.name()).equals(normalized)
                    || normalize(category.label).equals(normalized)) {
                return category;
            }
        }
        return OTHER;
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
