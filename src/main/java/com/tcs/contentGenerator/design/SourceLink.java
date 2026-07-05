package com.tcs.contentGenerator.design;

/**
 * Provenance from a design component back to the article it was drawn from,
 * identified the same way the fact-validation and brand-compliance agents
 * already do (section title + article headline, not a synthetic id) — so the
 * #2-#6 fact chain reaches all the way into the final artifact.
 */
public record SourceLink(String sectionTitle, String articleHeadline) {
}
