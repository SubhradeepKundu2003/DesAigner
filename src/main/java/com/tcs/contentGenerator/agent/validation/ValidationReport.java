package com.tcs.contentGenerator.agent.validation;

import java.util.List;

/**
 * The Fact Validation agent's output: every {@link ValidationFlag} raised across
 * the issue, how many articles were checked vs. skipped (the Leadership Message
 * has no source material by design), and whether export is blocked — true while
 * any flag at or above the configured blocking severity is unresolved.
 */
public record ValidationReport(
        List<ValidationFlag> flags,
        int articlesChecked,
        int articlesSkipped,
        boolean exportBlocked) {

    public ValidationReport {
        flags = flags == null ? List.of() : List.copyOf(flags);
    }

    public long countAtLeast(ValidationSeverity severity) {
        return flags.stream().filter(f -> f.severity().meetsOrExceeds(severity)).count();
    }
}
