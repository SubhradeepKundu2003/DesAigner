package com.tcs.contentGenerator.agent.review;

/**
 * How serious a {@link ReviewFinding} is. Declaration order is lowest to
 * highest, mirroring {@code ValidationSeverity} — used only to weight the
 * quality score here, not to gate export.
 */
public enum FindingSeverity {

    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String label;

    FindingSeverity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Lenient parse of a model-supplied severity (matches name or label, any
     * case). Unknown values fall back to {@link #MEDIUM} so a stray label
     * never breaks the run.
     */
    public static FindingSeverity fromLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String normalized = raw.strip().toLowerCase();
        for (FindingSeverity severity : values()) {
            if (severity.name().toLowerCase().equals(normalized)
                    || severity.label.toLowerCase().equals(normalized)) {
                return severity;
            }
        }
        return MEDIUM;
    }
}
