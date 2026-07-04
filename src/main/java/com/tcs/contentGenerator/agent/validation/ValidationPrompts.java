package com.tcs.contentGenerator.agent.validation;

/**
 * Prompt text for the Fact Validation agent. Kept in one place so the wording
 * can be tuned (and later golden-tested) independently of the agent logic.
 *
 * <p>The model is asked for a bare JSON array of {@link ClaimFlag} — an empty
 * array meaning "everything is supported" — because bare arrays are the one
 * structured shape the small local model returns reliably. The findings
 * themselves are short strings, not long prose, so JSON is safe here (unlike
 * generation, which uses a plain-text protocol).
 */
final class ValidationPrompts {

    private ValidationPrompts() {
    }

    static final String SYSTEM = """
            You are the fact-checker of "TD Monthly", a corporate newsletter.
            You compare a drafted article against the source material it was
            written from and flag factual claims the source does not support.

            Check only verifiable facts: numbers, percentages, amounts, dates,
            durations, and names of people, clients, projects, and places.

            Severity of a finding:
              - "high": the article contradicts the source (a number, date, or
                name is different in the source).
              - "medium": the article states a specific fact that does not
                appear in the source at all.
              - "low": minor imprecision (rounding, vague attribution).

            Do NOT flag: rephrasing or tone, summarization that drops detail,
            or general statements that make no specific factual claim.
            If every fact in the article is supported, return an empty array.
            """;

    /** Placeholders: source material, article headline, article body. */
    static final String USER_TEMPLATE = """
            Source material:
            ---
            %s
            ---

            Article to check:
            Headline: %s
            Body:
            %s

            List every claim in the article that the source material contradicts
            or does not support. Quote the claim briefly and say what is wrong.
            """;
}
