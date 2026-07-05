package com.tcs.contentGenerator.design;

/**
 * An image slot. {@code assetId} references an {@link Asset} on the
 * {@link DesignDocument} — {@code null} until the graphics agent (or a human,
 * in the editor) fills it, in which case renderers draw an explicit
 * placeholder rather than nothing.
 */
public record ImageBox(String id, ComponentRole role, Frame frame, int z, boolean locked,
        SourceLink source, String assetId, String altText) implements Component {
}
