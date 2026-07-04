package com.tcs.contentGenerator.agent.generation;

import java.util.List;

import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * One fully written section of the issue: which {@link NewsletterSection} it is
 * and its articles, in the order the planning agent ranked them.
 */
public record GeneratedSection(NewsletterSection section, List<GeneratedArticle> articles) {

    public GeneratedSection {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
