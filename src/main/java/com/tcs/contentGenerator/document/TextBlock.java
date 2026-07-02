package com.tcs.contentGenerator.document;

/** A paragraph or free-text run. */
public record TextBlock(String text, SourceRef source) implements DocumentBlock {
}
