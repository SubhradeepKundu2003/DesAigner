package com.tcs.contentGenerator.agent.understanding;

import java.util.List;

import com.tcs.contentGenerator.document.SourceRef;

/**
 * A single structured, newsletter-worthy unit of content identified by the
 * Content Understanding agent (a project, achievement, event, metric, ...),
 * classified into a {@link BusinessCategory}. This is the hand-off shape consumed
 * by later agents (planning, generation, fact validation).
 *
 * <p>{@code sources} preserves provenance back to the originating document(s) —
 * de-duplication merges the source lists of overlapping items — so downstream
 * fact-checking can trace every claim to where it came from.
 */
public record ContentItem(
        String title,
        String summary,
        BusinessCategory category,
        ItemType type,
        List<String> keyMetrics,
        List<SourceRef> sources) {

    public ContentItem {
        keyMetrics = keyMetrics == null ? List.of() : List.copyOf(keyMetrics);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
