package com.tcs.contentGenerator.design;

/**
 * A component's position and size on its page, in points (1/72"). Absolute and
 * top-left-origin, like a PowerPoint shape — this is what makes one geometry
 * drive the HTML, PPTX, and PDF renderers with exact fidelity.
 */
public record Frame(double x, double y, double w, double h) {
}
