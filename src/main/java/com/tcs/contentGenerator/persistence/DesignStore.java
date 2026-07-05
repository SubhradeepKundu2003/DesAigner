package com.tcs.contentGenerator.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tcs.contentGenerator.design.DesignDocument;

import tools.jackson.databind.ObjectMapper;

/**
 * Persistence boundary for {@link DesignDocument}s: serializes the design tree
 * to JSON (stored as Postgres {@code jsonb} on {@link DesignRecord}) and back.
 * The pipeline writes the initial revision; every later save must present the
 * revision it loaded and goes through an atomic guarded update — a stale save
 * raises {@link StaleRevisionException} instead of overwriting newer work.
 *
 * <p>Uses the application's own Jackson mapper — the same one Spring MVC uses
 * for the editor API — so the stored JSON is exactly the wire payload.
 */
@Service
public class DesignStore {

    private final DesignRecordRepository repository;
    private final ObjectMapper mapper;

    public DesignStore(DesignRecordRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Persists a pipeline-produced design under its job id (first write, no lock check). */
    @Transactional
    public void saveNew(String jobId, DesignDocument document) {
        repository.save(new DesignRecord(jobId, document.revision(),
                mapper.writeValueAsString(document), Instant.now()));
    }

    @Transactional(readOnly = true)
    public Optional<DesignDocument> load(String jobId) {
        return repository.findById(jobId)
                .map(record -> mapper.readValue(record.getDocument(), DesignDocument.class));
    }

    @Transactional(readOnly = true)
    public boolean exists(String jobId) {
        return repository.existsById(jobId);
    }

    /**
     * Applies an editor save. {@code incoming.revision()} must be the revision
     * the editor loaded; the stored document is replaced and the revision bumped
     * in one guarded statement. Returns the document as stored (revision + 1) so
     * the editor can keep saving without a reload.
     */
    @Transactional
    public DesignDocument saveEdit(String jobId, DesignDocument incoming) {
        long expectedRevision = incoming.revision();
        DesignDocument bumped = new DesignDocument(incoming.schemaVersion(), expectedRevision + 1,
                incoming.meta(), incoming.theme(), incoming.assets(), incoming.pages());
        int rows = repository.updateIfRevisionMatches(jobId,
                mapper.writeValueAsString(bumped), expectedRevision, Instant.now());
        if (rows == 0) {
            if (!repository.existsById(jobId)) {
                throw new DesignNotFoundException(jobId);
            }
            throw new StaleRevisionException(jobId, expectedRevision);
        }
        return bumped;
    }
}
