package com.tcs.contentGenerator.agent.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.agent.understanding.DocumentChunker;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.SourceRef;

/**
 * Walks a {@link ContentItem}'s provenance back to source text the fact-checker
 * can read. The understanding agent stamps items with document-level
 * {@link SourceRef}s whose {@code location} is either {@code "document"} or
 * {@code "chunk i/n"}, so this resolver re-chunks the referenced document with
 * the same (deterministic, same-config) {@link DocumentChunker} the
 * understanding agent used and returns exactly the chunk(s) the item was
 * extracted from — not the whole document, which could blow the model's
 * context window.
 */
@Component
public class SourceTextResolver {

    private static final Pattern CHUNK_LOCATION = Pattern.compile("chunk (\\d+)/(\\d+)");

    private final DocumentChunker chunker;
    private final int maxSourceChars;

    public SourceTextResolver(DocumentChunker chunker,
            @Value("${app.validation.max-source-chars:6000}") int maxSourceChars) {
        this.chunker = chunker;
        this.maxSourceChars = maxSourceChars;
    }

    /**
     * Renders the source text behind {@code item}, capped at
     * {@code app.validation.max-source-chars}. Returns an empty string when no
     * referenced document can be found (the article then can't be fact-checked).
     */
    public String resolve(ContentItem item, List<DocumentModel> documents) {
        // De-duplication can merge refs pointing at the same chunk — render each once.
        Set<String> seen = new LinkedHashSet<>();
        List<String> pieces = new ArrayList<>();
        for (SourceRef ref : item.sources()) {
            if (!seen.add(ref.documentName() + "|" + ref.location())) {
                continue;
            }
            String text = resolve(ref, documents);
            if (!text.isBlank()) {
                pieces.add(text);
            }
        }
        String joined = String.join("\n\n", pieces);
        return joined.length() <= maxSourceChars ? joined : joined.substring(0, maxSourceChars);
    }

    private String resolve(SourceRef ref, List<DocumentModel> documents) {
        DocumentModel document = documents.stream()
                .filter(d -> d.metadata().originalFilename().equals(ref.documentName()))
                .findFirst()
                .orElse(null);
        if (document == null) {
            return "";
        }
        List<String> chunks = chunker.chunk(document);
        if (chunks.isEmpty()) {
            return "";
        }
        Matcher m = CHUNK_LOCATION.matcher(ref.location() == null ? "" : ref.location());
        if (m.find()) {
            int index = Integer.parseInt(m.group(1)) - 1;
            if (index >= 0 && index < chunks.size()) {
                return chunks.get(index);
            }
        }
        // "document" (single-chunk doc) or an unparseable location: use everything.
        return String.join("\n\n", chunks);
    }
}
