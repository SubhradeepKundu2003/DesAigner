package com.tcs.contentGenerator.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DesignRecordRepository extends JpaRepository<DesignRecord, String> {

    /**
     * The optimistic-lock save: applies an edited design only if the stored
     * revision is still the one the editor loaded, bumping it in the same
     * statement. Returns the number of rows updated — {@code 0} means the row
     * is missing or someone saved a newer revision first (a stale save), and
     * the caller must not treat the edit as applied. Native SQL so the
     * {@code jsonb} cast is explicit and the check-and-bump is one atomic
     * statement with no read-modify-write race.
     */
    @Modifying
    @Query(value = """
            update design_documents
               set document = cast(:document as jsonb),
                   revision = :expectedRevision + 1,
                   updated_at = :now
             where job_id = :jobId
               and revision = :expectedRevision
            """, nativeQuery = true)
    int updateIfRevisionMatches(@Param("jobId") String jobId,
            @Param("document") String document,
            @Param("expectedRevision") long expectedRevision,
            @Param("now") Instant now);
}
