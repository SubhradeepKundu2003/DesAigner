package com.tcs.contentGenerator.web;

import java.util.List;

import com.tcs.contentGenerator.agent.planning.NewsletterPlan;
import com.tcs.contentGenerator.agent.planning.PlannedItem;
import com.tcs.contentGenerator.agent.planning.SectionPlan;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.HeadingBlock;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.TableBlock;
import com.tcs.contentGenerator.document.TextBlock;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/** Summary of a pipeline run returned to the caller. */
public record IngestionResponse(
        String jobId,
        List<DocumentSummary> documents,
        List<ContentItemSummary> contentItems,
        PlanSummary plan) {

    public record DocumentSummary(
            String filename,
            String type,
            int totalBlocks,
            int headings,
            int textBlocks,
            int tables,
            int images) {
    }

    /** Flattened view of a classified {@link ContentItem} for the API response. */
    public record ContentItemSummary(
            String title,
            String category,
            String type,
            String summary,
            List<String> keyMetrics,
            int sources) {
    }

    /** The planned issue: sections in reading order plus what was left out and why. */
    public record PlanSummary(
            String issueTitle,
            List<SectionSummary> sections,
            List<PlannedItemSummary> deferredItems) {
    }

    public record SectionSummary(String title, List<PlannedItemSummary> items) {
    }

    public record PlannedItemSummary(String title, String category, int score, String rationale) {
    }

    public static IngestionResponse from(PipelineContext context) {
        List<DocumentSummary> summaries = context.getDocuments().stream()
                .map(IngestionResponse::summarize)
                .toList();
        List<ContentItemSummary> items = context.getContentItems().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new IngestionResponse(context.getJobId(), summaries, items,
                summarize(context.getNewsletterPlan()));
    }

    private static PlanSummary summarize(NewsletterPlan plan) {
        if (plan == null) {
            return null;
        }
        List<SectionSummary> sections = plan.sections().stream()
                .map(IngestionResponse::summarize)
                .toList();
        List<PlannedItemSummary> deferred = plan.deferredItems().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new PlanSummary(plan.issueTitle(), sections, deferred);
    }

    private static SectionSummary summarize(SectionPlan section) {
        List<PlannedItemSummary> items = section.items().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new SectionSummary(section.section().title(), items);
    }

    private static PlannedItemSummary summarize(PlannedItem planned) {
        return new PlannedItemSummary(
                planned.item().title(),
                planned.item().category().label(),
                planned.score(),
                planned.rationale());
    }

    private static ContentItemSummary summarize(ContentItem item) {
        return new ContentItemSummary(
                item.title(),
                item.category().label(),
                item.type().name(),
                item.summary(),
                item.keyMetrics(),
                item.sources().size());
    }

    private static DocumentSummary summarize(DocumentModel doc) {
        return new DocumentSummary(
                doc.metadata().originalFilename(),
                doc.metadata().type().name(),
                doc.blocks().size(),
                doc.blocksOf(HeadingBlock.class).size(),
                doc.blocksOf(TextBlock.class).size(),
                doc.blocksOf(TableBlock.class).size(),
                doc.blocksOf(ImageBlock.class).size());
    }
}
