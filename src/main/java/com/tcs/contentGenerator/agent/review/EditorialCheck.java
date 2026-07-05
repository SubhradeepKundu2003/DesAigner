package com.tcs.contentGenerator.agent.review;

/**
 * One element of the bare JSON array the editorial-review LLM call returns
 * (per the extractJson lesson: lists come back as bare arrays, never a
 * wrapper object).
 */
public record EditorialCheck(String category, String severity, String issue) {
}
