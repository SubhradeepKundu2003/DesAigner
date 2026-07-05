package com.tcs.contentGenerator.design;

import java.util.List;

/**
 * The renderer-independent design tree for one issue — the single source of
 * truth the editor loads/saves and every renderer (HTML/PPTX/PDF) exports.
 * {@code revision} is for optimistic locking once the editor can save this
 * back (Phase C); the pipeline always produces revision 1.
 */
public record DesignDocument(int schemaVersion, long revision, DesignMeta meta, Theme theme,
        List<Asset> assets, List<Page> pages) {

    public DesignDocument {
        assets = assets == null ? List.of() : List.copyOf(assets);
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}
