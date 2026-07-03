package com.tcs.contentGenerator.orchestrator;

import java.util.ArrayList;
import java.util.List;

import com.tcs.contentGenerator.agent.planning.NewsletterPlan;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Mutable state carried through the agent pipeline. Each agent reads the output
 * of earlier stages and appends its own: the ingestion agent fills in the
 * normalized {@link DocumentModel}s, the understanding agent adds the classified
 * {@link ContentItem}s, the planning agent sets the {@link NewsletterPlan}, and
 * later stages (generation, ...) will add their own fields.
 */
public class PipelineContext {

    private final String jobId;
    private final List<StoredFile> inputFiles;
    private final List<DocumentModel> documents = new ArrayList<>();
    private final List<ContentItem> contentItems = new ArrayList<>();
    private NewsletterPlan newsletterPlan;

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

    public List<ContentItem> getContentItems() {
        return List.copyOf(contentItems);
    }

    public void addContentItem(ContentItem item) {
        contentItems.add(item);
    }

    /** {@code null} until the planning agent has run. */
    public NewsletterPlan getNewsletterPlan() {
        return newsletterPlan;
    }

    public void setNewsletterPlan(NewsletterPlan newsletterPlan) {
        this.newsletterPlan = newsletterPlan;
    }

    /** Storage prefix under which extracted images for this job are kept. */
    public String imageDir() {
        return "jobs/" + jobId + "/images";
    }
}
