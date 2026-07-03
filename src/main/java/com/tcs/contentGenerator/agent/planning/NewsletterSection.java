package com.tcs.contentGenerator.agent.planning;

import com.tcs.contentGenerator.agent.understanding.BusinessCategory;

/**
 * The recurring sections of the newsletter, declared in the order they appear in
 * the final issue. Each content-bearing section maps 1:1 from a
 * {@link BusinessCategory}; {@link #LEADERSHIP_MESSAGE} has no source category —
 * it carries no extracted items and is written from scratch by the Content
 * Generation agent.
 */
public enum NewsletterSection {

    LEADERSHIP_MESSAGE("Leadership Message", null),
    DELIVERY_HIGHLIGHTS("Delivery Highlights", BusinessCategory.DELIVERY_HIGHLIGHTS),
    PROJECT_UPDATES("Project Updates", BusinessCategory.PROJECT_UPDATES),
    INNOVATION_SPOTLIGHT("Innovation Spotlight", BusinessCategory.TECHNOLOGY_INITIATIVES),
    CUSTOMER_SUCCESS("Customer Success", BusinessCategory.CUSTOMER_SUCCESS),
    AWARDS_AND_RECOGNITION("Awards & Recognition", BusinessCategory.AWARDS_AND_RECOGNITION),
    TRAINING_AND_LEARNING("Training & Learning", BusinessCategory.TRAINING_AND_LEARNING),
    UPCOMING_EVENTS("Upcoming Events", BusinessCategory.EVENTS),
    IN_OTHER_NEWS("In Other News", BusinessCategory.OTHER);

    private final String title;
    private final BusinessCategory sourceCategory;

    NewsletterSection(String title, BusinessCategory sourceCategory) {
        this.title = title;
        this.sourceCategory = sourceCategory;
    }

    public String title() {
        return title;
    }

    /** The section a classified item belongs to, driven by its business category. */
    public static NewsletterSection fromCategory(BusinessCategory category) {
        for (NewsletterSection section : values()) {
            if (section.sourceCategory == category) {
                return section;
            }
        }
        return IN_OTHER_NEWS;
    }
}
