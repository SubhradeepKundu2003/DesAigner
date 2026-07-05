package com.tcs.contentGenerator.agent.compliance;

/**
 * One brand-rule breach found in a generated article. {@code sectionTitle} +
 * {@code articleHeadline} identify the article (the headline is the final,
 * post-fix one, so rows match what the newsletter now says); {@code found} is
 * the offending wording as written, {@code replacement} what it was changed to
 * ({@code ""} for banned phrases, which have no drop-in replacement), and
 * {@code fixed} whether the text was actually corrected — {@code false} means
 * the article still contains the wording and needs a human editor.
 */
public record ComplianceViolation(
        String sectionTitle,
        String articleHeadline,
        ViolationType type,
        String found,
        String replacement,
        boolean fixed) {

    public ComplianceViolation {
        found = found == null ? "" : found.strip();
        replacement = replacement == null ? "" : replacement.strip();
    }
}
