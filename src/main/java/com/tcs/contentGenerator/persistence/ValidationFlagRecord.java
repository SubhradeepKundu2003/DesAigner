package com.tcs.contentGenerator.persistence;

import java.time.Instant;

import com.tcs.contentGenerator.agent.validation.ValidationFlag;
import com.tcs.contentGenerator.agent.validation.ValidationSeverity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One persisted fact-validation finding for a job. The pipeline writes these
 * from the {@link com.tcs.contentGenerator.agent.validation.ValidationReport}
 * at the end of a run; afterwards an editor resolves (or waives) them through
 * the flags API. The export gate reads the *live* resolved state here — not the
 * report's point-in-time {@code exportBlocked} — so resolving the last blocking
 * flag is what unblocks export.
 */
@Entity
@Table(name = "validation_flags",
        indexes = @Index(name = "idx_validation_flags_job", columnList = "job_id"))
public class ValidationFlagRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", length = 64, nullable = false)
    private String jobId;

    @Column(name = "section_title")
    private String sectionTitle;

    @Column(name = "article_headline")
    private String articleHeadline;

    @Column(columnDefinition = "text")
    private String claim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ValidationSeverity severity;

    @Column(columnDefinition = "text")
    private String issue;

    @Column(nullable = false)
    private boolean resolved;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ValidationFlagRecord() {
        // JPA only
    }

    public ValidationFlagRecord(String jobId, ValidationFlag flag, Instant now) {
        this.jobId = jobId;
        this.sectionTitle = flag.sectionTitle();
        this.articleHeadline = flag.articleHeadline();
        this.claim = flag.claim();
        this.severity = flag.severity();
        this.issue = flag.issue();
        this.resolved = false;
        this.createdAt = now;
    }

    /** Marks the flag resolved/waived; the note records the editor's reasoning. */
    public void resolve(String note, Instant when) {
        this.resolved = true;
        this.resolutionNote = note;
        this.resolvedAt = when;
    }

    public Long getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getArticleHeadline() {
        return articleHeadline;
    }

    public String getClaim() {
        return claim;
    }

    public ValidationSeverity getSeverity() {
        return severity;
    }

    public String getIssue() {
        return issue;
    }

    public boolean isResolved() {
        return resolved;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
