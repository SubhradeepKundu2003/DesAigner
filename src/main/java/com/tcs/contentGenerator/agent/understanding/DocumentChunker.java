package com.tcs.contentGenerator.agent.understanding;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.document.DocumentBlock;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.HeadingBlock;

/**
 * Splits a rendered document into chunks that fit the LLM's context window. A
 * large document sent as one prompt is silently truncated by the model runtime
 * (observed live: a ~150K-token docx collapsed to a single extracted item), so
 * the understanding agent analyzes one chunk at a time and de-duplicates across
 * chunks afterwards.
 *
 * <p>Chunking is structure-aware: the document is first split into sections at
 * top-level headings (level 1–2), then whole sections are packed greedily into
 * chunks of at most {@code app.understanding.max-chunk-chars} characters. A
 * single section larger than the budget is split at paragraph boundaries, with
 * the section heading repeated on continuation chunks so the model keeps the
 * topical context.
 */
@Component
public class DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunker.class);

    /** Headings at this level or above start a new section. */
    private static final int SECTION_HEADING_LEVEL = 2;

    private final DocumentTextRenderer renderer;
    private final int maxChunkChars;
    private final int maxChunksPerDocument;

    public DocumentChunker(DocumentTextRenderer renderer,
            @Value("${app.understanding.max-chunk-chars:12000}") int maxChunkChars,
            @Value("${app.understanding.max-chunks-per-document:40}") int maxChunksPerDocument) {
        this.renderer = renderer;
        this.maxChunkChars = maxChunkChars;
        this.maxChunksPerDocument = maxChunksPerDocument;
    }

    /** Renders the document as a list of chunk texts, each within the size budget. */
    public List<String> chunk(DocumentModel document) {
        List<String> sections = renderSections(document);
        List<String> chunks = pack(sections);

        if (chunks.size() > maxChunksPerDocument) {
            log.warn("[{}] document produced {} chunk(s); analyzing only the first {} "
                    + "(raise app.understanding.max-chunks-per-document to cover more)",
                    document.metadata().originalFilename(), chunks.size(), maxChunksPerDocument);
            chunks = chunks.subList(0, maxChunksPerDocument);
        }
        return chunks;
    }

    /** Renders each heading-delimited section of the document to text. */
    private List<String> renderSections(DocumentModel document) {
        List<String> sections = new ArrayList<>();
        List<DocumentBlock> current = new ArrayList<>();
        for (DocumentBlock block : document.blocks()) {
            if (block instanceof HeadingBlock h && h.level() <= SECTION_HEADING_LEVEL
                    && !current.isEmpty()) {
                addRendered(sections, current);
                current = new ArrayList<>();
            }
            current.add(block);
        }
        addRendered(sections, current);
        return sections;
    }

    private void addRendered(List<String> sections, List<DocumentBlock> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        String text = renderer.render(blocks);
        if (!text.isBlank()) {
            sections.add(text);
        }
    }

    /** Greedily packs whole sections into chunks; oversized sections are split. */
    private List<String> pack(List<String> sections) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String section : sections) {
            if (section.length() > maxChunkChars) {
                flush(chunks, current);
                splitOversized(chunks, section);
                continue;
            }
            if (current.length() + section.length() + 2 > maxChunkChars) {
                flush(chunks, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(section);
        }
        flush(chunks, current);
        return chunks;
    }

    /**
     * Splits one section that alone exceeds the budget at paragraph boundaries,
     * repeating its heading line (if any) on each continuation chunk.
     */
    private void splitOversized(List<String> chunks, String section) {
        String heading = section.startsWith("#") ? section.lines().findFirst().orElse("") : "";
        String continuationPrefix = heading.isBlank() ? "" : heading + " (continued)";

        StringBuilder current = new StringBuilder();
        for (String paragraph : section.split("\n\n")) {
            // A single paragraph beyond the budget is truncated rather than split further.
            if (paragraph.length() > maxChunkChars) {
                paragraph = paragraph.substring(0, maxChunkChars);
            }
            if (current.length() + paragraph.length() + 2 > maxChunkChars) {
                flush(chunks, current);
                current.append(continuationPrefix);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        flush(chunks, current);
    }

    private void flush(List<String> chunks, StringBuilder current) {
        String text = current.toString().strip();
        if (!text.isBlank()) {
            chunks.add(text);
        }
        current.setLength(0);
    }
}
