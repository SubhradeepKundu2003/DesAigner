package com.tcs.contentGenerator.document;

import java.util.Locale;

/** Supported source-document formats. */
public enum DocumentType {
    XLSX,
    DOCX,
    PDF,
    PPTX,
    UNKNOWN;

    /** Detect the type from a filename extension. */
    public static DocumentType fromFilename(String filename) {
        if (filename == null) {
            return UNKNOWN;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) {
            return XLSX;
        }
        if (lower.endsWith(".docx")) {
            return DOCX;
        }
        if (lower.endsWith(".pdf")) {
            return PDF;
        }
        if (lower.endsWith(".pptx")) {
            return PPTX;
        }
        return UNKNOWN;
    }
}
