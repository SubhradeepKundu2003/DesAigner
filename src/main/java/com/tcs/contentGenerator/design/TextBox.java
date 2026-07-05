package com.tcs.contentGenerator.design;

/**
 * A run of text. {@code styleRef} names a {@link TextStyle} in the document's
 * {@link Theme} (e.g. {@code "Headline"}) — restyling the theme restyles every
 * box that references it, rather than each box carrying its own font.
 */
public record TextBox(String id, ComponentRole role, Frame frame, int z, boolean locked,
        SourceLink source, String styleRef, String text) implements Component {
}
