package com.tcs.contentGenerator.agent.understanding;

/**
 * The kind of newsletter-worthy item identified in a source document. Distinct
 * from {@link BusinessCategory}: {@code type} is what the item <em>is</em> (an
 * event, a metric, ...), while the category is which section it belongs to.
 */
public enum ItemType {

    PROJECT,
    ACHIEVEMENT,
    EVENT,
    METRIC,
    ANNOUNCEMENT,
    MILESTONE;

    /**
     * Lenient parse of a model-supplied type. Unknown or blank values fall back to
     * {@link #ANNOUNCEMENT} — the most generic bucket — so the pipeline is robust
     * to small-model output drift.
     */
    public static ItemType fromLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return ANNOUNCEMENT;
        }
        String normalized = raw.toLowerCase().replaceAll("[^a-z0-9]", "");
        for (ItemType type : values()) {
            if (type.name().toLowerCase().equals(normalized)) {
                return type;
            }
        }
        return ANNOUNCEMENT;
    }
}
