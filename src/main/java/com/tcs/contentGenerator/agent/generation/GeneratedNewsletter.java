package com.tcs.contentGenerator.agent.generation;

import java.util.List;

/**
 * Output of the Content Generation agent: the full written issue — title plus
 * every section's articles in final reading order. Downstream agents (fact
 * validation, brand compliance, layout) consume and refine this.
 */
public record GeneratedNewsletter(String issueTitle, List<GeneratedSection> sections) {

    public GeneratedNewsletter {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    /** Total number of articles written for the issue. */
    public int articleCount() {
        return sections.stream().mapToInt(s -> s.articles().size()).sum();
    }
}
