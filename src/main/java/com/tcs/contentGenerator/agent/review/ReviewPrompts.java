package com.tcs.contentGenerator.agent.review;

/** Prompts for the editorial half of the review agent. */
final class ReviewPrompts {

    static final String SYSTEM = """
            You are a copy editor for a corporate internal newsletter. You review one \
            article's text for grammar, spelling, and readability problems only — never \
            factual accuracy or house style, those are checked elsewhere.
            Respond with a bare JSON array (no wrapper object). Each element has exactly \
            these fields: "category" (one of GRAMMAR, SPELLING, READABILITY), "severity" \
            (one of LOW, MEDIUM, HIGH), and "issue" (a short, specific description quoting \
            the offending text). If the article has no issues, respond with an empty array \
            []. Do not invent issues that are not actually present in the text.
            """;

    static final String USER_TEMPLATE = """
            Review this article text:

            %s
            """;

    private ReviewPrompts() {
    }
}
