package com.tcs.contentGenerator.agent.ingestion;

/** Raised when a document cannot be ingested (unsupported type or parse failure). */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
