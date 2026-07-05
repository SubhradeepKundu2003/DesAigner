package com.tcs.contentGenerator.storage;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction over where uploaded files and extracted images live. The local
 * filesystem implementation is used for now; the interface keeps agents unaware
 * of storage details so it can be swapped for S3/SharePoint later.
 */
public interface StorageService {

    /**
     * Persist content at the given relative path and return the storage ref
     * (the same relative path) used to retrieve it later.
     */
    String store(String relativePath, byte[] content);

    /** Read back previously stored content by its ref. */
    byte[] retrieve(String ref);

    /** Resolve a ref to an absolute path (for rendering/export). */
    Path resolve(String ref);

    /** Delete stored content; no-op if it does not exist. */
    void delete(String ref);

    /**
     * Refs of the regular files directly under {@code relativeDir}, sorted for
     * deterministic selection. Empty (not an error) if the directory doesn't exist.
     */
    List<String> list(String relativeDir);
}
