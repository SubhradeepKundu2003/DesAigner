package com.tcs.contentGenerator.agent.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tcs.contentGenerator.agent.design.infographic.InfographicSpec;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * One section after composition: which pattern its articles will be laid out
 * with, plus (only for {@link SectionPattern#STAT_CALLOUT}) the value/label
 * pulled out of the article's key metric, (only for
 * {@link SectionPattern#KPI_TILES}) the row of {@link Stat}s pulled out of the
 * article's key metrics, and (only for {@link SectionPattern#INFOGRAPHIC})
 * the chosen {@link InfographicSpec} with the article's points. No
 * coordinates yet — that's the layout engine's job. {@code iconAssetId} is
 * non-null only when a real icon file exists for the section under
 * {@code assets/ICONS/} — null keeps the colored-dot fallback drawn from
 * {@code iconColorRole}. {@code pointIconRefs}, when present, is parallel to
 * {@code points}: each entry is either a real per-point icon's asset id
 * (matched by {@code IconMatcher.matchPointIcon}) or {@code null}, meaning
 * that point's disc/badge/node keeps its painted number.
 */
public record SectionComposition(NewsletterSection section, SectionPattern pattern,
        List<GeneratedArticle> articles, String iconColorRole, String iconAssetId,
        String statValue, String statLabel, List<Stat> stats,
        InfographicSpec infographic, List<GeneratedArticle.Point> points,
        List<String> pointIconRefs) {

    public SectionComposition {
        articles = articles == null ? List.of() : List.copyOf(articles);
        stats = stats == null ? List.of() : List.copyOf(stats);
        points = points == null ? List.of() : List.copyOf(points);
        // may contain nulls (points with no icon match) — List.copyOf forbids those, so wrap instead
        pointIconRefs = pointIconRefs == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(pointIconRefs));
    }

    /** No KPI tiles and no infographic — the common case; keeps existing call sites unchanged. */
    public SectionComposition(NewsletterSection section, SectionPattern pattern,
            List<GeneratedArticle> articles, String iconColorRole, String iconAssetId,
            String statValue, String statLabel) {
        this(section, pattern, articles, iconColorRole, iconAssetId, statValue, statLabel,
                List.of(), null, List.of(), List.of());
    }

    /** KPI tiles, no infographic — keeps the KPI call sites unchanged. */
    public SectionComposition(NewsletterSection section, SectionPattern pattern,
            List<GeneratedArticle> articles, String iconColorRole, String iconAssetId,
            String statValue, String statLabel, List<Stat> stats) {
        this(section, pattern, articles, iconColorRole, iconAssetId, statValue, statLabel,
                stats, null, List.of(), List.of());
    }

    /** Infographic, no per-point icon matches — keeps the pre-icon infographic call sites unchanged. */
    public SectionComposition(NewsletterSection section, SectionPattern pattern,
            List<GeneratedArticle> articles, String iconColorRole, String iconAssetId,
            String statValue, String statLabel, List<Stat> stats,
            InfographicSpec infographic, List<GeneratedArticle.Point> points) {
        this(section, pattern, articles, iconColorRole, iconAssetId, statValue, statLabel,
                stats, infographic, points, List.of());
    }

    /** One KPI tile: a big value ("72", "18%") over a short label ("NPS", "growth"). */
    public record Stat(String value, String label) {
    }
}
