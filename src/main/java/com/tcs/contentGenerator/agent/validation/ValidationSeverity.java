package com.tcs.contentGenerator.agent.validation;

/**
 * How serious a {@link ValidationFlag} is. Declaration order is lowest to
 * highest so severities can be compared with {@link #meetsOrExceeds}; the
 * export gate blocks on flags at or above a configurable threshold
 * ({@code app.validation.blocking-severity}).
 */
public enum ValidationSeverity {

    /** Minor imprecision (rounding, vague attribution) — informational. */
    LOW("Low"),
    /** A specific factual claim the source material does not support. */
    MEDIUM("Medium"),
    /** The article contradicts the source (wrong number, date, or name). */
    HIGH("High");

    private final String label;

    ValidationSeverity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True when this severity is at least as serious as {@code other}. */
    public boolean meetsOrExceeds(ValidationSeverity other) {
        return compareTo(other) >= 0;
    }

    /**
     * Lenient parse of a model-supplied severity (matches name or label, any
     * case). Unknown values fall back to {@link #MEDIUM}: visible in the report
     * but not blocking under the default gate, so a stray label never breaks or
     * needlessly halts a run.
     */
    public static ValidationSeverity fromLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String normalized = raw.strip().toLowerCase();
        for (ValidationSeverity severity : values()) {
            if (severity.name().toLowerCase().equals(normalized)
                    || severity.label.toLowerCase().equals(normalized)) {
                return severity;
            }
        }
        return MEDIUM;
    }
}
