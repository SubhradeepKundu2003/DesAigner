package com.tcs.contentGenerator.design;

/**
 * A reusable resource (currently only images) referenced by id from an
 * {@link ImageBox}. {@code storedRef} points into {@code StorageService},
 * never raw bytes, mirroring how {@code ImageBlock} works in the ingestion model.
 */
public record Asset(String id, String kind, String storedRef, Integer width, Integer height) {
}
