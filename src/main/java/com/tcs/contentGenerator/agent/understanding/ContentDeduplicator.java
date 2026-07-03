package com.tcs.contentGenerator.agent.understanding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.document.SourceRef;

/**
 * Collapses overlapping {@link ContentItem}s produced across documents. The
 * prototype uses a lightweight heuristic: items whose titles normalize to the same
 * key are treated as duplicates and merged, unioning their {@code sources} and
 * {@code keyMetrics} so provenance is never lost.
 *
 * <p>This is deliberately simple; semantic de-duplication via {@code nomic-embed-text}
 * embeddings (to catch reworded duplicates) is a planned upgrade.
 */
@Component
public class ContentDeduplicator {

    public List<ContentItem> deduplicate(List<ContentItem> items) {
        Map<String, ContentItem> byKey = new LinkedHashMap<>();
        for (ContentItem item : items) {
            String key = normalizeTitle(item.title());
            ContentItem existing = byKey.get(key);
            byKey.put(key, existing == null ? item : merge(existing, item));
        }
        return new ArrayList<>(byKey.values());
    }

    private ContentItem merge(ContentItem base, ContentItem duplicate) {
        List<SourceRef> sources = new ArrayList<>(base.sources());
        duplicate.sources().stream().filter(s -> !sources.contains(s)).forEach(sources::add);

        List<String> metrics = new ArrayList<>(base.keyMetrics());
        duplicate.keyMetrics().stream().filter(m -> !metrics.contains(m)).forEach(metrics::add);

        return new ContentItem(
                base.title(),
                base.summary(),
                base.category(),
                base.type(),
                metrics,
                sources);
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : title.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
