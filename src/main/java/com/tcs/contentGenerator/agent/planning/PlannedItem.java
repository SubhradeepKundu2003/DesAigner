package com.tcs.contentGenerator.agent.planning;

import com.tcs.contentGenerator.agent.understanding.ContentItem;

/**
 * A {@link ContentItem} after planning: the impact score (1–10) the model gave it
 * and a one-line rationale. Items in a {@link SectionPlan} made the issue;
 * items in {@link NewsletterPlan#deferredItems()} were scored too low or overflowed
 * their section's cap.
 */
public record PlannedItem(ContentItem item, int score, String rationale) {

    public PlannedItem {
        rationale = rationale == null ? "" : rationale.strip();
    }
}
