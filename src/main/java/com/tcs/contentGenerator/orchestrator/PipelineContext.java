package com.tcs.contentGenerator.orchestrator;

import java.util.ArrayList;
import java.util.List;

import com.tcs.contentGenerator.agent.compliance.ComplianceReport;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.graphics.GraphicsReport;
import com.tcs.contentGenerator.agent.planning.NewsletterPlan;
import com.tcs.contentGenerator.agent.review.ReviewReport;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.agent.validation.ValidationReport;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Mutable state carried through the agent pipeline. Each agent reads the output
 * of earlier stages and appends its own: the ingestion agent fills in the
 * normalized {@link DocumentModel}s, the understanding agent adds the classified
 * {@link ContentItem}s, the planning agent sets the {@link NewsletterPlan}, the
 * generation agent sets the {@link GeneratedNewsletter}, the fact validation
 * agent sets the {@link ValidationReport}, the brand compliance agent sets the
 * {@link ComplianceReport} (and replaces the {@link GeneratedNewsletter} with
 * the corrected text), the design composition agent sets the {@link DesignDocument},
 * the image graphics agent sets the {@link GraphicsReport} (and replaces the
 * {@link DesignDocument} with the image-enriched one), and the review agent
 * sets the {@link ReviewReport} — the last stage before export.
 */
public class PipelineContext {

    private final String jobId;
    private final List<StoredFile> inputFiles;
    private final List<DocumentModel> documents = new ArrayList<>();
    private final List<ContentItem> contentItems = new ArrayList<>();
    private NewsletterPlan newsletterPlan;
    private GeneratedNewsletter generatedNewsletter;
    private ValidationReport validationReport;
    private ComplianceReport complianceReport;
    private DesignDocument designDocument;
    private GraphicsReport graphicsReport;
    private ReviewReport reviewReport;

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

    /** {@code null} until the generation agent has run. */
    public GeneratedNewsletter getGeneratedNewsletter() {
        return generatedNewsletter;
    }

    public void setGeneratedNewsletter(GeneratedNewsletter generatedNewsletter) {
        this.generatedNewsletter = generatedNewsletter;
    }

    /** {@code null} until the fact validation agent has run. */
    public ValidationReport getValidationReport() {
        return validationReport;
    }

    public void setValidationReport(ValidationReport validationReport) {
        this.validationReport = validationReport;
    }

    /** {@code null} until the brand compliance agent has run. */
    public ComplianceReport getComplianceReport() {
        return complianceReport;
    }

    public void setComplianceReport(ComplianceReport complianceReport) {
        this.complianceReport = complianceReport;
    }

    /** {@code null} until the design composition agent has run. */
    public DesignDocument getDesignDocument() {
        return designDocument;
    }

    public void setDesignDocument(DesignDocument designDocument) {
        this.designDocument = designDocument;
    }

    /** {@code null} until the image graphics agent has run. */
    public GraphicsReport getGraphicsReport() {
        return graphicsReport;
    }

    public void setGraphicsReport(GraphicsReport graphicsReport) {
        this.graphicsReport = graphicsReport;
    }

    /** {@code null} until the review agent has run. */
    public ReviewReport getReviewReport() {
        return reviewReport;
    }

    public void setReviewReport(ReviewReport reviewReport) {
        this.reviewReport = reviewReport;
    }

    /** Storage prefix under which extracted images for this job are kept. */
    public String imageDir() {
        return "jobs/" + jobId + "/images";
    }
}
