package com.tcs.contentGenerator.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Filesystem-backed {@link StorageService}. All refs are relative paths resolved
 * under a configurable root directory ({@code app.storage.root}, default {@code ./storage}).
 */
@Service
public class LocalFileStorageService implements StorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${app.storage.root:./storage}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String store(String relativePath, byte[] content) {
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store " + relativePath, e);
        }
        return relativePath;
    }

    @Override
    public byte[] retrieve(String ref) {
        try {
            return Files.readAllBytes(resolve(ref));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + ref, e);
        }
    }

    @Override
    public Path resolve(String ref) {
        Path resolved = root.resolve(ref).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Ref escapes storage root: " + ref);
        }
        return resolved;
    }

    @Override
    public void delete(String ref) {
        try {
            Files.deleteIfExists(resolve(ref));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + ref, e);
        }
    }

    @Override
    public List<String> list(String relativeDir) {
        Path dir = resolve(relativeDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + relativeDir, e);
        }
    }
}
