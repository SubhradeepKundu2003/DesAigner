package com.tcs.contentGenerator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.tcs.contentGenerator.agent.validation.ValidationFlag;
import com.tcs.contentGenerator.agent.validation.ValidationSeverity;

/**
 * The export gate's threshold semantics: which stored severities count as
 * blocking under the configured {@code app.validation.blocking-severity}, and
 * that resolving is scoped to the owning job.
 */
class FlagStoreTest {

    private final ValidationFlagRepository repository = mock(ValidationFlagRepository.class);

    @Test
    void highThresholdBlocksOnHighOnly() {
        FlagStore store = new FlagStore(repository, "high");
        when(repository.countByJobIdAndResolvedFalseAndSeverityIn(eq("job"), anyCollection()))
                .thenReturn(1L);

        assertTrue(store.exportBlocked("job"));
        assertEquals(Set.of(ValidationSeverity.HIGH), capturedSeverities("job"));
    }

    @Test
    void mediumThresholdBlocksOnMediumAndHigh() {
        FlagStore store = new FlagStore(repository, "medium");
        when(repository.countByJobIdAndResolvedFalseAndSeverityIn(eq("job"), anyCollection()))
                .thenReturn(0L);

        assertFalse(store.exportBlocked("job"));
        assertEquals(Set.of(ValidationSeverity.MEDIUM, ValidationSeverity.HIGH),
                capturedSeverities("job"));
    }

    @Test
    void resolvingAnotherJobsFlagIsNotFound() {
        FlagStore store = new FlagStore(repository, "high");
        ValidationFlagRecord otherJobs = new ValidationFlagRecord("other-job",
                new ValidationFlag("Section", "Headline", "claim", ValidationSeverity.HIGH, "issue"),
                Instant.now());
        when(repository.findById(7L)).thenReturn(Optional.of(otherJobs));

        assertThrows(FlagNotFoundException.class, () -> store.resolve("job", 7, "waived"));
    }

    @Test
    void resolveMarksFlagWithNote() {
        FlagStore store = new FlagStore(repository, "high");
        ValidationFlagRecord flag = new ValidationFlagRecord("job",
                new ValidationFlag("Section", "Headline", "claim", ValidationSeverity.HIGH, "issue"),
                Instant.now());
        when(repository.findById(7L)).thenReturn(Optional.of(flag));

        ValidationFlagRecord resolved = store.resolve("job", 7, "checked against source");

        assertTrue(resolved.isResolved());
        assertEquals("checked against source", resolved.getResolutionNote());
    }

    @SuppressWarnings("unchecked")
    private Set<ValidationSeverity> capturedSeverities(String jobId) {
        ArgumentCaptor<java.util.Collection<ValidationSeverity>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(repository).countByJobIdAndResolvedFalseAndSeverityIn(eq(jobId), captor.capture());
        return Set.copyOf(captor.getValue());
    }
}
