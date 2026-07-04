package com.tcs.contentGenerator.agent.validation;

/**
 * The JSON shape the LLM returns for one fact-check finding. The model is asked
 * for a bare array of these ({@code ClaimFlag[]}), not a wrapper object — the
 * small local model reliably emits bare arrays but not wrappers. Every field is
 * a plain string and may come back null or empty; the agent null-guards and
 * parses {@code severity} leniently when mapping to {@link ValidationFlag}.
 */
public record ClaimFlag(String claim, String severity, String issue) {
}
