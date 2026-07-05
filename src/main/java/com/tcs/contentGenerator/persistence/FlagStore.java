package com.tcs.contentGenerator.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tcs.contentGenerator.agent.validation.ValidationFlag;
import com.tcs.contentGenerator.agent.validation.ValidationSeverity;

/**
 * Persistence boundary for fact-validation flags and home of the live export
 * gate. The pipeline stores each run's flags; editors resolve/waive them via
 * the flags API; {@link #exportBlocked} recomputes the gate from the current
 * resolved state — same threshold config ({@code app.validation.blocking-severity})
 * the validation agent used at generation time.
 */
@Service
public class FlagStore {

    private final ValidationFlagRepository repository;
    private final Set<ValidationSeverity> blockingSeverities;

    public FlagStore(ValidationFlagRepository repository,
            @Value("${app.validation.blocking-severity:high}") String blockingSeverity) {
        this.repository = repository;
        ValidationSeverity threshold = ValidationSeverity.fromLabel(blockingSeverity);
        this.blockingSeverities = Arrays.stream(ValidationSeverity.values())
                .filter(severity -> severity.meetsOrExceeds(threshold))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Persists a pipeline run's flags, all initially unresolved. */
    @Transactional
    public void saveNew(String jobId, List<ValidationFlag> flags) {
        Instant now = Instant.now();
        repository.saveAll(flags.stream()
                .map(flag -> new ValidationFlagRecord(jobId, flag, now))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationFlagRecord> findByJob(String jobId) {
        return repository.findByJobIdOrderByIdAsc(jobId);
    }

    /** The export gate: true while any unresolved flag at/above the blocking severity exists. */
    @Transactional(readOnly = true)
    public boolean exportBlocked(String jobId) {
        return repository.countByJobIdAndResolvedFalseAndSeverityIn(jobId, blockingSeverities) > 0;
    }

    /** Resolves (or waives) one flag; the note records the editor's reasoning. */
    @Transactional
    public ValidationFlagRecord resolve(String jobId, long flagId, String note) {
        ValidationFlagRecord flag = repository.findById(flagId)
                .filter(found -> found.getJobId().equals(jobId))
                .orElseThrow(() -> new FlagNotFoundException(jobId, flagId));
        flag.resolve(note, Instant.now());
        return flag;
    }
}
