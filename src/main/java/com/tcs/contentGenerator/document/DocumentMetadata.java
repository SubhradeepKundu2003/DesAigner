package com.tcs.contentGenerator.document;

import java.time.Instant;

/** Descriptive metadata about a single ingested source document. */
public record DocumentMetadata(
        String originalFilename,
        DocumentType type,
        String storedRef,
        long sizeBytes,
        Instant ingestedAt) {
}
