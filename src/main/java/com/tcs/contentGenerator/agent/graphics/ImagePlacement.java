package com.tcs.contentGenerator.agent.graphics;

/** One image the graphics agent placed next to an article. */
public record ImagePlacement(String sectionTitle, String articleHeadline, ImageSource source, String storedRef) {
}
