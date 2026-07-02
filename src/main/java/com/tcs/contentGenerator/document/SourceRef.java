package com.tcs.contentGenerator.document;

/**
 * Provenance pointer attached to every content block: which document it came
 * from, where inside it (e.g. "Sheet:Q3", "Page:2", "Slide:4"), and the block's
 * ordinal within that document. Downstream agents (especially Fact Validation)
 * rely on this to trace generated content back to its source.
 */
public record SourceRef(String documentName, String location, int sequence) {
}
