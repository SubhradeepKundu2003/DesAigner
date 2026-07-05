package com.tcs.contentGenerator.persistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No such validation flag for the given job (wrong id, or a different job's flag). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class FlagNotFoundException extends RuntimeException {

    public FlagNotFoundException(String jobId, long flagId) {
        super("No validation flag " + flagId + " for job " + jobId);
    }
}
