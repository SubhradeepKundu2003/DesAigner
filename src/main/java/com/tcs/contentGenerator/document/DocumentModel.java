package com.tcs.contentGenerator.document;

import java.util.List;

/**
 * The normalized, format-agnostic representation of one source document. This is
 * the common contract the ingestion agent produces and every later agent reads.
 */
public record DocumentModel(DocumentMetadata metadata, List<DocumentBlock> blocks) {

    public DocumentModel {
        blocks = List.copyOf(blocks);
    }

    /** All blocks of a given kind, in order. */
    public <T extends DocumentBlock> List<T> blocksOf(Class<T> type) {
        return blocks.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
