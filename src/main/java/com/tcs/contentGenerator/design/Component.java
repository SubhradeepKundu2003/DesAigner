package com.tcs.contentGenerator.design;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * One positioned element on a {@link Page}. Sealed so every renderer (and the
 * future Angular editor) can exhaustively switch over the three shapes it will
 * ever need to draw. Every component carries a {@link #frame()} (the only
 * source of geometry — the LLM never produces one) and a {@link #source()}
 * back to the article it came from, {@code null} for template-only decoration
 * (dividers, icons).
 *
 * <p>{@code DEDUCTION} lets Jackson tell {@link TextBox}/{@link ImageBox}/
 * {@link ShapeBox} apart from their distinct field sets alone — no synthetic
 * type-discriminator field cluttering the JSON the editor reads and writes.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
public sealed interface Component permits TextBox, ImageBox, ShapeBox {

    String id();

    ComponentRole role();

    Frame frame();

    int z();

    boolean locked();

    SourceLink source();
}
