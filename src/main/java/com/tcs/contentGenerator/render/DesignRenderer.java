package com.tcs.contentGenerator.render;

import com.tcs.contentGenerator.design.DesignDocument;

/**
 * Exports a {@link DesignDocument} to one concrete format. Renderers only
 * read the model — they never adjust geometry (that's the layout engine's
 * job) and never talk to the LLM.
 */
public interface DesignRenderer {

    ExportFormat format();

    byte[] render(DesignDocument document);
}
