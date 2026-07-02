package com.tcs.contentGenerator.agent.ingestion;

/**
 * Per-run context handed to extractors. Carries the job id and the storage
 * prefix under which extracted images should be written.
 */
public record ExtractionContext(String jobId, String imageDir) {
}
