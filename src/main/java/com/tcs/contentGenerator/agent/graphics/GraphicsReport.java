package com.tcs.contentGenerator.agent.graphics;

import java.util.List;

/**
 * The Image & Graphics agent's output: how many article slots were candidates
 * for an image, how many actually got one, and where each image came from.
 * The enriched {@code DesignDocument} (with the new {@code ImageBox}es and
 * {@code Asset}s) replaces the one on the pipeline context; this report is the
 * record of what changed.
 */
public record GraphicsReport(int articlesConsidered, int imagesPlaced, List<ImagePlacement> placements) {

    public GraphicsReport {
        placements = placements == null ? List.of() : List.copyOf(placements);
    }
}
