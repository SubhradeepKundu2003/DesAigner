package com.tcs.contentGenerator.agent.design;

import java.util.List;

import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * One section after composition: which pattern its articles will be laid out
 * with, plus (only for {@link SectionPattern#STAT_CALLOUT}) the value/label
 * pulled out of the article's key metric. No coordinates yet — that's the
 * layout engine's job. {@code iconAssetId} is non-null only when a real icon
 * file exists for the section under {@code assets/ICONS/} — null keeps the
 * colored-dot fallback drawn from {@code iconColorRole}.
 */
public record SectionComposition(NewsletterSection section, SectionPattern pattern,
        List<GeneratedArticle> articles, String iconColorRole, String iconAssetId,
        String statValue, String statLabel) {

    public SectionComposition {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
