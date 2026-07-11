package com.tcs.contentGenerator.agent.design.infographic;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import com.tcs.contentGenerator.agent.generation.GeneratedArticle.Point;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * Chooses the infographic design for a section's points the way a designer
 * would — <em>filter by fit, then randomize among the survivors</em>:
 *
 * <ol>
 *   <li>hard filters: item count within the spec's range, page background
 *       compatibility, text lengths within the spec's slot capacities, and
 *       {@code wantsNumbers} specs only when every point carries a figure;</li>
 *   <li>intent: cheap keyword signals admit specialist archetypes (sequence
 *       words → {@code TIMELINE}, cycle words → {@code CYCLE}, all-numeric →
 *       {@code KPI_BARS}); {@code NUMBERED_LIST}/{@code CARD_GRID} are the
 *       always-admitted generalists. {@code HUB_SPOKE}/{@code SPLIT_VISUAL}
 *       stay signal-gated and currently have no signal — revisit when their
 *       specs land;</li>
 *   <li>a random pick among what survives, seeded by jobId + section name so
 *       re-rendering the same job is stable while different issues vary;</li>
 *   <li>variety: a design already used this issue is never picked again
 *       (one instance per run — the composition agent creates one).</li>
 * </ol>
 *
 * Purely deterministic (the seeded {@link Random} included) — no LLM call, per
 * the locked "LLM never produces geometry" rule. An empty result means "no
 * infographic earned": the caller falls through to the ordinary patterns.
 */
public class InfographicSelector {

    private static final int MIN_POINTS = 3;
    private static final Pattern SEQUENCE_WORDS = Pattern.compile(
            "(?i)\\b(step|phase|stage|milestone|roadmap|journey|timeline|quarter|q[1-4]|week|month|"
            + "january|february|march|april|may|june|july|august|september|october|november|december|"
            + "first|second|third|then|next|finally)\\b");
    private static final Pattern CYCLE_WORDS = Pattern.compile(
            "(?i)\\b(cycle|lifecycle|loop|continuous|recurring|iterat\\w*)\\b");
    private static final Pattern FIGURE = Pattern.compile("\\d");

    private final List<InfographicSpec> library;
    private final boolean darkBackground;
    private final String jobId;
    private final Set<String> usedThisIssue = new HashSet<>();

    public InfographicSelector(List<InfographicSpec> library, String jobId, boolean darkBackground) {
        this.library = List.copyOf(library);
        this.jobId = jobId == null ? "" : jobId;
        this.darkBackground = darkBackground;
    }

    /**
     * The design for this section's points, or empty when nothing fits —
     * never force an infographic onto content that hasn't earned one.
     */
    public Optional<InfographicSpec> select(NewsletterSection section, List<Point> points) {
        if (points.size() < MIN_POINTS) {
            return Optional.empty();
        }
        Set<InfographicSpec.Archetype> admitted = admittedArchetypes(points);
        List<InfographicSpec> candidates = library.stream()
                .filter(spec -> points.size() >= spec.minItems() && points.size() <= spec.maxItems())
                .filter(this::backgroundCompatible)
                .filter(spec -> !spec.wantsNumbers() || allPointsCarryFigures(points))
                .filter(spec -> textFits(spec, points))
                .filter(spec -> admitted.contains(spec.archetype()))
                .filter(spec -> !usedThisIssue.contains(spec.name()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // seeded by job + section: the same job re-renders identically, the
        // next issue rolls differently
        Random random = new Random(Objects.hash(jobId, section.name()));
        InfographicSpec pick = candidates.get(random.nextInt(candidates.size()));
        usedThisIssue.add(pick.name());
        return Optional.of(pick);
    }

    private Set<InfographicSpec.Archetype> admittedArchetypes(List<Point> points) {
        // the safe generalists fit any enumerable content
        Set<InfographicSpec.Archetype> admitted = EnumSet.of(
                InfographicSpec.Archetype.NUMBERED_LIST, InfographicSpec.Archetype.CARD_GRID);
        String labels = String.join(" ", points.stream().map(Point::label).toList());
        if (SEQUENCE_WORDS.matcher(labels).find()) {
            admitted.add(InfographicSpec.Archetype.TIMELINE);
        }
        if (CYCLE_WORDS.matcher(labels).find()) {
            admitted.add(InfographicSpec.Archetype.CYCLE);
        }
        if (allPointsCarryFigures(points)) {
            admitted.add(InfographicSpec.Archetype.KPI_BARS);
        }
        return admitted;
    }

    private boolean backgroundCompatible(InfographicSpec spec) {
        return switch (spec.background()) {
            case ANY -> true;
            case DARK -> darkBackground;
            case LIGHT -> !darkBackground;
        };
    }

    private static boolean allPointsCarryFigures(List<Point> points) {
        return points.stream().allMatch(point ->
                FIGURE.matcher(point.label()).find() || FIGURE.matcher(point.text()).find());
    }

    /** Coarse character filter; the fine check is real text measurement at layout time. */
    private static boolean textFits(InfographicSpec spec, List<Point> points) {
        return points.stream().allMatch(point ->
                point.label().length() <= spec.titleCapacity()
                        && point.text().length() <= spec.bodyCapacity());
    }
}
