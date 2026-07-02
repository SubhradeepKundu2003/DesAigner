package com.tcs.contentGenerator.document;

/**
 * A single unit of normalized content extracted from a source document. Sealed
 * so every downstream agent can exhaustively pattern-match over the known kinds.
 */
public sealed interface DocumentBlock
        permits HeadingBlock, TextBlock, TableBlock, ImageBlock {

    /** Where this block came from in the source document. */
    SourceRef source();
}
