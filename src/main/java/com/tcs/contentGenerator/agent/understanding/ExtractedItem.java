package com.tcs.contentGenerator.agent.understanding;

import java.util.List;

/**
 * Raw shape of a single extracted item as the LLM phrases it, with string-typed
 * category/type fields. The agent asks the model for a JSON <em>array</em> of
 * these (small models reliably emit a top-level array), then translates each into
 * a validated {@link ContentItem} — parsing the enums leniently and attaching
 * provenance.
 */
public record ExtractedItem(
        String title,
        String summary,
        String category,
        String type,
        List<String> keyMetrics) {
}
