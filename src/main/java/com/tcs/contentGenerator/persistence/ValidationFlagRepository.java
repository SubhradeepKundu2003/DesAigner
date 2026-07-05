package com.tcs.contentGenerator.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tcs.contentGenerator.agent.validation.ValidationSeverity;

public interface ValidationFlagRepository extends JpaRepository<ValidationFlagRecord, Long> {

    List<ValidationFlagRecord> findByJobIdOrderByIdAsc(String jobId);

    /** Backs the export gate: unresolved flags at any of the blocking severities. */
    long countByJobIdAndResolvedFalseAndSeverityIn(String jobId, Collection<ValidationSeverity> severities);
}
