package com.tcs.contentGenerator.agent.planning;

/**
 * LLM JSON DTO for the planning agent's scoring call. The model is asked for a
 * bare array of these (not a wrapper object — see the {@code extractJson} note in
 * TASKS.md: this local model reliably emits arrays, not wrappers). {@code index}
 * refers back to the numbered item list sent in the prompt.
 */
record ScoredItem(int index, int score, String rationale) {
}
