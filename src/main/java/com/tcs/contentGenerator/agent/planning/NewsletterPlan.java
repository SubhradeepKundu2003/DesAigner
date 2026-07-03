package com.tcs.contentGenerator.agent.planning;

import java.util.List;

/**
 * Output of the Content Planning agent and the input contract for Content
 * Generation: the issue's title, its sections in final reading order (each with
 * its selected items), and the items that did not make the cut this month
 * (scored below the threshold or overflowed a section's cap) — kept for
 * transparency and possible carry-over to a future issue.
 */
public record NewsletterPlan(
        String issueTitle,
        List<SectionPlan> sections,
        List<PlannedItem> deferredItems) {

    public NewsletterPlan {
        sections = sections == null ? List.of() : List.copyOf(sections);
        deferredItems = deferredItems == null ? List.of() : List.copyOf(deferredItems);
    }

    /** Total number of content items that made the issue. */
    public int selectedItemCount() {
        return sections.stream().mapToInt(s -> s.items().size()).sum();
    }
}
