package com.tcs.contentGenerator.web;

import java.util.List;

import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.HeadingBlock;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.TableBlock;
import com.tcs.contentGenerator.document.TextBlock;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/** Summary of an ingestion run returned to the caller. */
public record IngestionResponse(String jobId, List<DocumentSummary> documents) {

    public record DocumentSummary(
            String filename,
            String type,
            int totalBlocks,
            int headings,
            int textBlocks,
            int tables,
            int images) {
    }

    public static IngestionResponse from(PipelineContext context) {
        List<DocumentSummary> summaries = context.getDocuments().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new IngestionResponse(context.getJobId(), summaries);
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
