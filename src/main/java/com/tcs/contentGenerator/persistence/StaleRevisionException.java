package com.tcs.contentGenerator.persistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * An editor tried to save a design based on a revision that is no longer the
 * stored one — someone else saved first. The editor must reload
 * ({@code GET /api/designs/{id}}) and reapply its changes.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class StaleRevisionException extends RuntimeException {

    public StaleRevisionException(String jobId, long staleRevision) {
        super("Design for job " + jobId + " has moved past revision " + staleRevision
                + " — reload the latest revision and reapply the edit");
    }
}
