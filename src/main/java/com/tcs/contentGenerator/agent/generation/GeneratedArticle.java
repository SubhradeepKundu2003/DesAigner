package com.tcs.contentGenerator.agent.generation;

import com.tcs.contentGenerator.agent.planning.PlannedItem;

/**
 * One written newsletter article: a reader-facing headline and a short body of
 * plain-text paragraphs (separated by blank lines). {@code source} links back to
 * the {@link PlannedItem} the article was written from — {@code null} only for
 * the Leadership Message, which has no source item — so fact validation can
 * trace every article to its origin.
 */
public record GeneratedArticle(String headline, String body, PlannedItem source) {

    public GeneratedArticle {
        headline = headline == null ? "" : headline.strip();
        body = body == null ? "" : body.strip();
    }
}
