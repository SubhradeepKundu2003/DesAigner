package com.tcs.contentGenerator.orchestrator;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the agent pipeline in order. Spring injects all {@link Agent} beans; their
 * sequence is controlled with {@code @Order} on each agent. Today the chain is
 * just the ingestion agent, but new agents slot in without changing this class.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final List<Agent> agents;

    public AgentOrchestrator(List<Agent> agents) {
        this.agents = agents;
    }

    /** Execute every agent in order against the given context. */
    public void run(PipelineContext context) {
        log.info("Starting pipeline for job {} with {} agent(s)", context.getJobId(), agents.size());
        for (Agent agent : agents) {
            log.info("[{}] running", agent.name());
            agent.execute(context);
            log.info("[{}] done", agent.name());
        }
        log.info("Pipeline complete for job {}", context.getJobId());
    }
}
