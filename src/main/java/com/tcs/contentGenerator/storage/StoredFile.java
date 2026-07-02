package com.tcs.contentGenerator.storage;

import com.tcs.contentGenerator.document.DocumentType;

/** A source file that has been persisted to storage, ready for extraction. */
public record StoredFile(String originalFilename, DocumentType type, String storedRef, long size) {
}
