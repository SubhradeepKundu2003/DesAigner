package com.tcs.contentGenerator.render;

import java.util.Locale;
import java.util.Optional;

/** Every export format a design document can be rendered to. */
public enum ExportFormat {

    HTML("text/html"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    PDF("application/pdf");

    private final String mediaType;

    ExportFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    /** Download extension, e.g. {@code pdf} — also what {@link #fromName} accepts. */
    public String fileExtension() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Case-insensitive parse of a user-supplied format name; empty when unknown. */
    public static Optional<ExportFormat> fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
