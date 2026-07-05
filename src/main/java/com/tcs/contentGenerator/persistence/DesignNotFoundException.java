package com.tcs.contentGenerator.persistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No persisted design exists for the requested job. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DesignNotFoundException extends RuntimeException {

    public DesignNotFoundException(String jobId) {
        super("No design found for job " + jobId);
    }
}
