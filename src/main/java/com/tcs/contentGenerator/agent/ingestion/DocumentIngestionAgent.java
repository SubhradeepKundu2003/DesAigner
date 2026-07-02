package com.tcs.contentGenerator.agent.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Agent #1 of the pipeline. Turns each uploaded source file into a normalized
 * {@link DocumentModel} by delegating to the format-specific extractor, and
 * appends the results to the {@link PipelineContext} for downstream agents.
 */
@Component
@Order(1)
public class DocumentIngestionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionAgent.class);

    private final ExtractorRegistry registry;

    public DocumentIngestionAgent(ExtractorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "Document Ingestion Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        ExtractionContext ctx = new ExtractionContext(context.getJobId(), context.imageDir());
        for (StoredFile file : context.getInputFiles()) {
            DocumentExtractor extractor = registry.forType(file.type());
            DocumentModel model = extractor.extract(file, ctx);
            context.addDocument(model);
            log.info("Ingested {} -> {} block(s)", file.originalFilename(), model.blocks().size());
        }
    }
}
