package com.tcs.contentGenerator.orchestrator;

import java.util.ArrayList;
import java.util.List;

import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Mutable state carried through the agent pipeline. Each agent reads the output
 * of earlier stages and appends its own. For now it holds the ingestion inputs
 * and the normalized {@link DocumentModel}s produced by the ingestion agent;
 * later stages (understanding, planning, ...) will add their own fields.
 */
public class PipelineContext {

    private final String jobId;
    private final List<StoredFile> inputFiles;
    private final List<DocumentModel> documents = new ArrayList<>();

    public PipelineContext(String jobId, List<StoredFile> inputFiles) {
        this.jobId = jobId;
        this.inputFiles = List.copyOf(inputFiles);
    }

    public String getJobId() {
        return jobId;
    }

    public List<StoredFile> getInputFiles() {
        return inputFiles;
    }

    public List<DocumentModel> getDocuments() {
        return List.copyOf(documents);
    }

    public void addDocument(DocumentModel document) {
        documents.add(document);
    }

    /** Storage prefix under which extracted images for this job are kept. */
    public String imageDir() {
        return "jobs/" + jobId + "/images";
    }
}
