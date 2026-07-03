package com.tcs.contentGenerator.agent.understanding;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.SourceRef;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Agent #2 of the pipeline and the first to call the LLM. For each ingested
 * {@link DocumentModel} it asks the model to identify newsletter-worthy items
 * (projects, achievements, events, metrics, announcements, milestones), classifies
 * them into a {@link BusinessCategory}, de-duplicates across documents, and appends
 * the resulting {@link ContentItem}s to the {@link PipelineContext}.
 *
 * <p>A model failure on one document is isolated and logged so it can't abort the
 * whole run; the remaining documents still contribute their items.
 */
@Component
@Order(2)
public class ContentUnderstandingAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ContentUnderstandingAgent.class);

    private final LlmClient llm;
    private final DocumentTextRenderer renderer;
    private final ContentDeduplicator deduplicator;

    public ContentUnderstandingAgent(LlmClient llm, DocumentTextRenderer renderer,
            ContentDeduplicator deduplicator) {
        this.llm = llm;
        this.renderer = renderer;
        this.deduplicator = deduplicator;
    }

    @Override
    public String name() {
        return "Content Understanding Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        List<ContentItem> collected = new ArrayList<>();
        for (DocumentModel document : context.getDocuments()) {
            collected.addAll(understand(document));
        }

        List<ContentItem> deduplicated = deduplicator.deduplicate(collected);
        deduplicated.forEach(context::addContentItem);
        log.info("Understanding produced {} content item(s) ({} before de-duplication)",
                deduplicated.size(), collected.size());
    }

    private List<ContentItem> understand(DocumentModel document) {
        String filename = document.metadata().originalFilename();
        String text = renderer.render(document);
        if (text.isBlank()) {
            log.info("[{}] no textual content to analyze, skipping", filename);
            return List.of();
        }

        try {
            ExtractedItem[] extracted = llm.generate(
                    UnderstandingPrompts.SYSTEM,
                    UnderstandingPrompts.USER_TEMPLATE.formatted(text),
                    ExtractedItem[].class);
            List<ContentItem> items = toContentItems(extracted, filename);
            log.info("[{}] extracted {} content item(s)", filename, items.size());
            return items;
        } catch (Exception e) {
            log.warn("[{}] content understanding failed, skipping document: {}", filename, e.toString());
            return List.of();
        }
    }

    private List<ContentItem> toContentItems(ExtractedItem[] extracted, String filename) {
        if (extracted == null) {
            return List.of();
        }
        List<ContentItem> items = new ArrayList<>();
        int sequence = 0;
        for (ExtractedItem raw : extracted) {
            if (raw == null || raw.title() == null || raw.title().isBlank()) {
                continue;
            }
            SourceRef source = new SourceRef(filename, "document", sequence++);
            items.add(new ContentItem(
                    raw.title().strip(),
                    raw.summary() == null ? "" : raw.summary().strip(),
                    BusinessCategory.fromLabel(raw.category()),
                    ItemType.fromLabel(raw.type()),
                    raw.keyMetrics(),
                    List.of(source)));
        }
        return items;
    }
}
