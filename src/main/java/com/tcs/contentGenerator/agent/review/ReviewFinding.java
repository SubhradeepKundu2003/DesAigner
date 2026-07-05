package com.tcs.contentGenerator.agent.review;

/**
 * One review finding, pointing at the component it concerns by id so a
 * future editor can highlight it directly rather than re-deriving location
 * from free text.
 */
public record ReviewFinding(FindingSource source, String category, FindingSeverity severity,
        String componentId, String message) {

    public ReviewFinding {
        category = category == null ? "" : category.strip();
        message = message == null ? "" : message.strip();
    }
}
