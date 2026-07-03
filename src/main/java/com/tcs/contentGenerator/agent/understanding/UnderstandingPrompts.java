package com.tcs.contentGenerator.agent.understanding;

/**
 * Prompt text for the Content Understanding agent. Kept in one place so the
 * wording can be tuned (and later golden-tested) independently of the agent logic.
 */
final class UnderstandingPrompts {

    private UnderstandingPrompts() {
    }

    static final String SYSTEM = """
            You are a content analyst preparing a corporate monthly newsletter.
            You read raw business documents and identify discrete, newsletter-worthy
            items: projects, achievements, events, metrics, announcements, and
            milestones.

            For each item you find, produce:
              - title: a short, specific headline (max ~12 words)
              - summary: 1-3 factual sentences describing the item
              - category: exactly one of PROJECT_UPDATES, AWARDS_AND_RECOGNITION,
                TRAINING_AND_LEARNING, DELIVERY_HIGHLIGHTS, CUSTOMER_SUCCESS,
                TECHNOLOGY_INITIATIVES, EVENTS, OTHER
              - type: exactly one of PROJECT, ACHIEVEMENT, EVENT, METRIC,
                ANNOUNCEMENT, MILESTONE
              - keyMetrics: any concrete numbers, dates, percentages, or names that
                appear (as short strings); empty list if none

            Return a JSON array whose elements are these item objects. Return an
            empty array [] if the document has no newsletter-worthy content.

            Rules:
              - Only extract facts stated in the document. Never invent details.
              - Merge trivially duplicated statements into a single item.
            """;

    /** {@code %s} is replaced with the flattened document text. */
    static final String USER_TEMPLATE = """
            Extract the newsletter-worthy items from the following document.

            --- DOCUMENT START ---
            %s
            --- DOCUMENT END ---
            """;
}
