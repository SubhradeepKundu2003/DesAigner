package com.tcs.contentGenerator.agent.compliance;

import java.util.List;

/**
 * The Brand Compliance agent's output: every rule breach found across the
 * issue (including ones that were auto-fixed, for transparency), how many
 * articles were checked, and how many had their text changed. The corrected
 * articles themselves replace the {@code GeneratedNewsletter} on the pipeline
 * context, so this report is the record of <em>what</em> changed.
 */
public record ComplianceReport(
        List<ComplianceViolation> violations,
        int articlesChecked,
        int articlesFixed) {

    public ComplianceReport {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public long fixedCount() {
        return violations.stream().filter(ComplianceViolation::fixed).count();
    }

    /** Breaches still present in the text — these need a human editor. */
    public long unresolvedCount() {
        return violations.stream().filter(v -> !v.fixed()).count();
    }
}
