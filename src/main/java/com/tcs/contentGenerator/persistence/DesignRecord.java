package com.tcs.contentGenerator.persistence;

import java.time.Instant;

import org.hibernate.annotations.ColumnTransformer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One job's {@link com.tcs.contentGenerator.design.DesignDocument} persisted as
 * Postgres {@code jsonb}. Hibernate never maps the design tree itself — the
 * JSON string is produced and consumed by {@link DesignStore} with the same
 * Jackson mapping the editor API uses, so what is stored is byte-for-byte what
 * the editor sends and receives (one schema, no drift).
 *
 * <p>{@code revision} mirrors the JSON's own revision field and is the
 * optimistic lock: editor saves go through a guarded
 * {@code UPDATE ... WHERE revision = expected} (see
 * {@link DesignRecordRepository#updateIfRevisionMatches}), so a stale save can
 * never silently overwrite a newer one.
 */
@Entity
@Table(name = "design_documents")
public class DesignRecord {

    @Id
    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(nullable = false)
    private long revision;

    /** Full design JSON; the write cast is needed because the driver binds a plain string. */
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String document;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DesignRecord() {
        // JPA only
    }

    public DesignRecord(String jobId, long revision, String document, Instant now) {
        this.jobId = jobId;
        this.revision = revision;
        this.document = document;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getJobId() {
        return jobId;
    }

    public long getRevision() {
        return revision;
    }

    public String getDocument() {
        return document;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
