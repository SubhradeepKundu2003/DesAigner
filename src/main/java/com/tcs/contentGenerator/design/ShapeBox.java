package com.tcs.contentGenerator.design;

/**
 * A plain decorative shape (a divider rule, a section-icon dot) with no text
 * or image content. {@code shapeType} is a small renderer-understood vocabulary
 * ({@code "rect"}, {@code "circle"}, {@code "line"}); {@code fillColorRole}
 * names a color in the theme rather than a literal color, so a rebrand is a
 * theme edit, not a document edit.
 */
public record ShapeBox(String id, ComponentRole role, Frame frame, int z, boolean locked,
        SourceLink source, String shapeType, String fillColorRole) implements Component {
}
