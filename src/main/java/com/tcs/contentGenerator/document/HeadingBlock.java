package com.tcs.contentGenerator.document;

/** A heading/title line. {@code level} 1 = top-level, 2 = sub-heading, etc. */
public record HeadingBlock(int level, String text, SourceRef source) implements DocumentBlock {
}
