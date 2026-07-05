package com.tcs.contentGenerator.agent.compliance;

/**
 * Prompt text for the Brand Compliance agent's rewrite call — used only when
 * an article contains banned wording that deterministic substitution cannot
 * fix. Same plain-text protocol as generation ("line 1 = headline, blank
 * line, body"): long prose in JSON is where the small local model breaks.
 */
final class CompliancePrompts {

    private CompliancePrompts() {
    }

    static final String SYSTEM = """
            You are the brand-compliance editor of "TD Monthly", a corporate
            monthly newsletter. You revise articles with the lightest possible
            touch so they comply with the house style.

            Rules:
              - Rephrase to remove the banned wording you are given; change
                nothing that is not required for the text to read well.
              - Never add, remove, or alter facts, figures, dates, or names.
              - Keep roughly the same length and the same professional,
                warm tone.

            Output format (STRICT):
              - Line 1: the revised headline only (no quotes, no markdown,
                no "Headline:" prefix).
              - Then a blank line, then the revised body as plain-text
                paragraphs separated by blank lines.
              - No markdown syntax, no commentary, nothing else.
            """;

    /** Placeholders: comma-joined banned wording, headline, body. */
    static final String REWRITE_TEMPLATE = """
            Revise this article so it no longer uses any of the following
            banned wording (in any casing): %s.

            Headline: %s

            Body:
            %s
            """;
}
