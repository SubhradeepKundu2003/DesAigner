package com.tcs.contentGenerator.agent.generation;

/**
 * Prompt text for the Content Generation agent. Kept in one place so the wording
 * can be tuned (and later golden-tested) independently of the agent logic.
 *
 * <p>Unlike the extraction/scoring agents this one asks for <em>plain text</em>,
 * not JSON: multi-paragraph prose inside JSON strings is exactly where the small
 * local model produces unparseable output (unescaped newlines), while a
 * "first line = headline, rest = body" protocol is unbreakable.
 */
final class GenerationPrompts {

    private GenerationPrompts() {
    }

    /** Shared voice for every call — this is what keeps the tone consistent. */
    static final String SYSTEM = """
            You are the staff writer of "TD Monthly", a corporate monthly
            newsletter for employees and stakeholders.

            House style:
              - Professional, warm, and positive; plain business English.
              - Active voice, short sentences, no jargon and no buzzwords.
              - Weave concrete numbers, dates, and names into the prose naturally.
              - Never invent facts, figures, names, or quotes. Only use what the
                input provides.

            Output format (STRICT):
              - Line 1: the headline only (max ~10 words, no quotes, no markdown,
                no "Headline:" prefix).
              - Then a blank line, then the article body as 1-3 short plain-text
                paragraphs separated by blank lines.
              - No markdown syntax, no bullet lists, no sign-off, nothing else.
            """;

    /**
     * Per-article prompt. Placeholders: section title, item title, item summary,
     * key-metrics line (may say "none"), issue title.
     */
    static final String ARTICLE_TEMPLATE = """
            Write one article of 80-120 words for the "%s" section of the issue
            "%s".

            Source item:
            Title: %s
            Summary: %s
            Key facts and figures: %s

            Rewrite this into reader-friendly newsletter prose. Give it a fresh,
            engaging headline (do not just repeat the source title).
            """;

    /**
     * Leadership Message prompt. Placeholders: issue title, highlights digest
     * (section names with their top story titles).
     */
    static final String LEADERSHIP_TEMPLATE = """
            Write the opening Leadership Message of 100-150 words for the issue
            "%s", voiced as the leadership team addressing all employees ("we",
            addressing "you" / "our teams").

            This month's issue covers:
            %s

            Thank the teams, briefly reflect on one or two of this month's themes
            (without re-telling the articles in detail), and close on an
            encouraging, forward-looking note. Do not use any individual's name.
            """;
}
