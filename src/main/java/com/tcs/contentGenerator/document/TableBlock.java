package com.tcs.contentGenerator.document;

import java.util.List;

/**
 * A tabular region. The first row of a source table is treated as {@code headers}
 * and the remainder as {@code rows}; when no header row is discernible, callers
 * may pass an empty header list.
 */
public record TableBlock(List<String> headers, List<List<String>> rows, SourceRef source)
        implements DocumentBlock {

    public TableBlock {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }
}
