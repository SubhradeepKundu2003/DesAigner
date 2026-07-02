package com.tcs.contentGenerator.document;

/**
 * An extracted image. The bytes live in storage; {@code storedRef} is the
 * storage key, never the raw content. {@code caption} may be null.
 */
public record ImageBlock(String storedRef, String caption, String mimeType, SourceRef source)
        implements DocumentBlock {
}
