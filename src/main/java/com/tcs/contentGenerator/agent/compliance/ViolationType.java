package com.tcs.contentGenerator.agent.compliance;

/**
 * What kind of brand-rule breach a {@link ComplianceViolation} is. Each type
 * maps to one rule list in {@link ComplianceRules} and to one fix strategy.
 */
public enum ViolationType {

    /** A term with an approved replacement — auto-fixed by string substitution. */
    TERMINOLOGY("Terminology"),
    /** A brand/product name written with the wrong casing — auto-fixed to canonical. */
    CASING("Casing"),
    /** Banned wording with no drop-in replacement — needs an LLM rewrite. */
    BANNED_PHRASE("Banned phrase");

    private final String label;

    ViolationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
