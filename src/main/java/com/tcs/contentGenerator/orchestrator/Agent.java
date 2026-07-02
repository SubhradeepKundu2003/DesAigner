package com.tcs.contentGenerator.orchestrator;

/**
 * Contract implemented by every agent in the newsletter-generation pipeline.
 * Agents read from and write to the shared {@link PipelineContext}.
 */
public interface Agent {

    /** Human-readable name, used for logging and status tracking. */
    String name();

    /** Execute this agent's stage, mutating the shared pipeline context. */
    void execute(PipelineContext context);
}
