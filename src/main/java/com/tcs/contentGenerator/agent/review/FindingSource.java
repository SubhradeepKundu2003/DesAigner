package com.tcs.contentGenerator.agent.review;

/** Which half of the review agent raised a {@link ReviewFinding}. */
public enum FindingSource {
    /** Deterministic geometry/contrast check, no LLM involved. */
    LAYOUT,
    /** LLM grammar/spelling/readability pass over an article's text. */
    EDITORIAL
}
