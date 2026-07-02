package com.tcs.contentGenerator.agent.ingestion.extractor;

import java.time.Instant;
import java.util.UUID;

import com.tcs.contentGenerator.agent.ingestion.ExtractionContext;
import com.tcs.contentGenerator.document.DocumentMetadata;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.storage.StoredFile;

/** Shared helpers for the concrete per-format extractors. */
public abstract class AbstractDocumentExtractor {

    protected final StorageService storage;

    protected AbstractDocumentExtractor(StorageService storage) {
        this.storage = storage;
    }

    /** Read the raw bytes of a stored source file. */
    protected byte[] read(StoredFile file) {
        return storage.retrieve(file.storedRef());
    }

    /** Build metadata for an extracted document. */
    protected DocumentMetadata metadata(StoredFile file) {
        return new DocumentMetadata(
                file.originalFilename(), file.type(), file.storedRef(), file.size(), Instant.now());
    }

    /** Persist an extracted image and return its storage ref. */
    protected String storeImage(ExtractionContext ctx, String extension, byte[] data) {
        String ext = extension == null || extension.isBlank() ? "img" : extension.replace(".", "");
        String name = ctx.jobId() + "-" + UUID.randomUUID() + "." + ext;
        return storage.store(ctx.imageDir() + "/" + name, data);
    }
}
