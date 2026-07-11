package com.tcs.contentGenerator.agent.generation;

import java.util.List;

import com.tcs.contentGenerator.agent.planning.PlannedItem;

/**
 * One written newsletter article: a reader-facing headline and a short body of
 * plain-text paragraphs (separated by blank lines). {@code source} links back to
 * the {@link PlannedItem} the article was written from — {@code null} only for
 * the Leadership Message, which has no source item — so fact validation can
 * trace every article to its origin.
 *
 * <p>{@code points} is the article's optional enumerable structure (3-6 steps,
 * pillars, categories...), emitted by the model via the plain-text
 * {@code POINT: label | text} protocol when the content genuinely has one —
 * empty for ordinary prose articles. Points feed infographic selection in the
 * design stage; they supplement the body, never replace it.
 */
public record GeneratedArticle(String headline, String body, PlannedItem source, List<Point> points) {

    public GeneratedArticle {
        headline = headline == null ? "" : headline.strip();
        body = body == null ? "" : body.strip();
        points = points == null ? List.of() : List.copyOf(points);
    }

    /** No points — ordinary prose article; keeps existing call sites unchanged. */
    public GeneratedArticle(String headline, String body, PlannedItem source) {
        this(headline, body, source, List.of());
    }

    /** One enumerable point: a short label and a one-line description. */
    public record Point(String label, String text) {

        public Point {
            label = label == null ? "" : label.strip();
            text = text == null ? "" : text.strip();
        }
    }
}
