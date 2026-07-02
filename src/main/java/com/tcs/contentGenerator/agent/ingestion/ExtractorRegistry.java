package com.tcs.contentGenerator.agent.ingestion;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.document.DocumentType;

/**
 * Resolves the right {@link DocumentExtractor} for a document type. Spring injects
 * every extractor bean, so adding a new format needs no change here.
 */
@Component
public class ExtractorRegistry {

    private final List<DocumentExtractor> extractors;

    public ExtractorRegistry(List<DocumentExtractor> extractors) {
        this.extractors = extractors;
    }

    public DocumentExtractor forType(DocumentType type) {
        return extractors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new IngestionException("No extractor available for type " + type));
    }
}
