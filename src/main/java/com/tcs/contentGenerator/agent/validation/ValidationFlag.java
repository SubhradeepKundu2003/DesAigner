package com.tcs.contentGenerator.agent.validation;

/**
 * One fact-check finding: a claim in a generated article that the source
 * material contradicts or does not support (or a note that an article could
 * not be checked). {@code sectionTitle} + {@code articleHeadline} identify the
 * article; {@code claim} quotes the suspect text (may be empty for
 * article-level notes) and {@code issue} says what is wrong.
 */
public record ValidationFlag(
        String sectionTitle,
        String articleHeadline,
        String claim,
        ValidationSeverity severity,
        String issue) {

    public ValidationFlag {
        claim = claim == null ? "" : claim.strip();
        issue = issue == null ? "" : issue.strip();
        severity = severity == null ? ValidationSeverity.MEDIUM : severity;
    }
}
