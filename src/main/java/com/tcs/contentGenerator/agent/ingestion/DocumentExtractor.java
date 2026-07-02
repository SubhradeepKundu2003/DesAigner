package com.tcs.contentGenerator.agent.ingestion;

import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.DocumentType;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Strategy for turning one stored source file into a normalized {@link DocumentModel}.
 * One implementation per file type; new formats are added by adding a bean.
 */
public interface DocumentExtractor {

    /** Whether this extractor handles the given document type. */
    boolean supports(DocumentType type);

    /** Extract text, tables, images and metadata from the file. */
    DocumentModel extract(StoredFile file, ExtractionContext context);
}
