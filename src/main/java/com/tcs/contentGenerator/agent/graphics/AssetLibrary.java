package com.tcs.contentGenerator.agent.graphics;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Looks up approved brand images for a newsletter section, used as the
 * fallback when no source-document image is available. Convention: one file
 * per approved image under {@code storage/assets/<SECTION_NAME>/}, with a
 * {@code storage/assets/GENERIC/} folder as the catch-all. Editors manage this
 * by dropping files in place — no config or code change needed to add one.
 */
@Component
public class AssetLibrary {

    private static final String GENERIC_FOLDER = "GENERIC";

    private final StorageService storage;
    private final String root;

    public AssetLibrary(StorageService storage,
            @Value("${app.graphics.brand-assets-root:assets}") String root) {
        this.storage = storage;
        this.root = root;
    }

    /** The first (alphabetically) approved image for this section, or its generic fallback. */
    public Optional<String> findFor(NewsletterSection section) {
        Optional<String> specific = firstAssetIn(section.name());
        return specific.isPresent() ? specific : firstAssetIn(GENERIC_FOLDER);
    }

    private Optional<String> firstAssetIn(String folder) {
        return storage.list(root + "/" + folder).stream().findFirst();
    }
}
