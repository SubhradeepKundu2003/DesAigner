package com.tcs.contentGenerator.agent.planning;

import java.util.List;

/**
 * One section of the planned issue: which {@link NewsletterSection} it is and the
 * items assigned to it, ordered by descending impact score. The
 * {@link NewsletterSection#LEADERSHIP_MESSAGE} section is planned with an empty
 * item list — the Content Generation agent writes it without source items.
 */
public record SectionPlan(NewsletterSection section, List<PlannedItem> items) {

    public SectionPlan {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
