package com.tcs.contentGenerator.agent.design.infographic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.generation.GeneratedArticle.Point;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * "Filter by fit, then randomize among survivors": the selector must never
 * hand out a design the points don't fit, must be reproducible per job, and
 * must not reuse a design within one issue.
 */
class InfographicSelectorTest {

    private static final InfographicSpec NUMBERED_BARS = new InfographicSpec(
            "numbered-bars", InfographicSpec.Archetype.NUMBERED_LIST, 3, 5, 60, 220,
            false, InfographicSpec.Background.ANY,
            new InfographicSpec.Shape("numberedBars", "primary", "text"));
    private static final InfographicSpec DARK_ONLY = new InfographicSpec(
            "dark-cards", InfographicSpec.Archetype.CARD_GRID, 3, 6, 60, 220,
            false, InfographicSpec.Background.DARK,
            new InfographicSpec.Shape("cards", "surface", "primary"));
    private static final InfographicSpec KPI = new InfographicSpec(
            "kpi-chevrons", InfographicSpec.Archetype.KPI_BARS, 3, 4, 60, 220,
            true, InfographicSpec.Background.ANY,
            new InfographicSpec.Shape("chevrons", "primary", "text"));

    private static List<Point> genericPoints(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> new Point("Pillar " + toWord(i), "A short description of this pillar."))
                .toList();
    }

    private static String toWord(int i) {
        return List.of("one", "two", "three", "four", "five", "six").get(i - 1);
    }

    @Test
    void picksAFittingDesignForEnumerablePoints() {
        InfographicSelector selector = new InfographicSelector(List.of(NUMBERED_BARS), "job-1", false);
        Optional<InfographicSpec> pick = selector.select(
                NewsletterSection.PROJECT_UPDATES, genericPoints(4));
        assertEquals("numbered-bars", pick.orElseThrow().name());
    }

    @Test
    void fewerThanThreePointsNeverEarnAnInfographic() {
        InfographicSelector selector = new InfographicSelector(List.of(NUMBERED_BARS), "job-1", false);
        assertTrue(selector.select(NewsletterSection.PROJECT_UPDATES, genericPoints(2)).isEmpty());
    }

    @Test
    void itemCountOutsideTheSpecRangeIsFilteredOut() {
        InfographicSelector selector = new InfographicSelector(List.of(NUMBERED_BARS), "job-1", false);
        assertTrue(selector.select(NewsletterSection.PROJECT_UPDATES, genericPoints(6)).isEmpty(),
                "six points exceed numbered-bars' maxItems of 5");
    }

    @Test
    void overlongLabelsAreFilteredOut() {
        List<Point> longPoints = List.of(
                new Point("x".repeat(100), "short"),
                new Point("ok", "short"),
                new Point("ok too", "short"));
        InfographicSelector selector = new InfographicSelector(List.of(NUMBERED_BARS), "job-1", false);
        assertTrue(selector.select(NewsletterSection.PROJECT_UPDATES, longPoints).isEmpty(),
                "a label past titleCapacity would clip in the slot");
    }

    @Test
    void darkOnlyDesignsAreSkippedOnLightPages() {
        InfographicSelector light = new InfographicSelector(List.of(DARK_ONLY), "job-1", false);
        assertTrue(light.select(NewsletterSection.PROJECT_UPDATES, genericPoints(3)).isEmpty());
        InfographicSelector dark = new InfographicSelector(List.of(DARK_ONLY), "job-1", true);
        assertEquals("dark-cards", dark.select(
                NewsletterSection.PROJECT_UPDATES, genericPoints(3)).orElseThrow().name());
    }

    @Test
    void numberWantingDesignsRequireEveryPointToCarryAFigure() {
        InfographicSelector selector = new InfographicSelector(List.of(KPI), "job-1", false);
        assertTrue(selector.select(NewsletterSection.DELIVERY_HIGHLIGHTS, genericPoints(3)).isEmpty(),
                "KPI archetype without numbers in the points");
        List<Point> numeric = List.of(
                new Point("NPS 72", "up from 68"),
                new Point("Growth 18%", "quarter on quarter"),
                new Point("CSAT 4.6/5", "customer satisfaction"));
        assertEquals("kpi-chevrons", selector.select(
                NewsletterSection.DELIVERY_HIGHLIGHTS, numeric).orElseThrow().name());
    }

    @Test
    void aDesignIsNeverReusedWithinOneIssue() {
        InfographicSelector selector = new InfographicSelector(List.of(NUMBERED_BARS), "job-1", false);
        assertTrue(selector.select(NewsletterSection.PROJECT_UPDATES, genericPoints(4)).isPresent());
        assertTrue(selector.select(NewsletterSection.INNOVATION_SPOTLIGHT, genericPoints(4)).isEmpty(),
                "the only design was already used — the next section falls back to plain patterns");
    }

    @Test
    void sameJobAlwaysPicksTheSameDesign() {
        for (int run = 0; run < 3; run++) {
            InfographicSelector selector = new InfographicSelector(
                    List.of(NUMBERED_BARS, cardGridVariant("a"), cardGridVariant("b")), "job-7", false);
            InfographicSpec first = new InfographicSelector(
                    List.of(NUMBERED_BARS, cardGridVariant("a"), cardGridVariant("b")), "job-7", false)
                    .select(NewsletterSection.PROJECT_UPDATES, genericPoints(4)).orElseThrow();
            assertEquals(first.name(), selector.select(
                    NewsletterSection.PROJECT_UPDATES, genericPoints(4)).orElseThrow().name(),
                    "re-rendering the same job must be stable");
        }
    }

    @Test
    void centralThemeWordsAdmitHubSpoke() {
        InfographicSpec hubSpoke = new InfographicSpec(
                "hub-spoke", InfographicSpec.Archetype.HUB_SPOKE, 3, 6, 60, 220,
                false, InfographicSpec.Background.ANY,
                new InfographicSpec.Shape("hubWheel", "primary", "secondary"));
        InfographicSelector selector = new InfographicSelector(List.of(hubSpoke), "job-1", false);
        List<Point> central = List.of(
                new Point("Data core", "The central platform every team builds on."),
                new Point("Delivery", "Feeds off the core."),
                new Point("Insights", "Feeds off the core."));
        assertEquals("hub-spoke", selector.select(
                NewsletterSection.PROJECT_UPDATES, central).orElseThrow().name(),
                "a central-theme word in a label admits HUB_SPOKE");
        InfographicSelector noSignal = new InfographicSelector(List.of(hubSpoke), "job-1", false);
        assertTrue(noSignal.select(NewsletterSection.PROJECT_UPDATES, genericPoints(3)).isEmpty(),
                "HUB_SPOKE stays signal-gated — plain points don't earn it");
    }

    @Test
    void splitVisualIsAlwaysAdmittedAsAGeneralistWithinItsItemRange() {
        InfographicSpec splitVisual = new InfographicSpec(
                "split-visual", InfographicSpec.Archetype.SPLIT_VISUAL, 3, 4, 30, 90,
                false, InfographicSpec.Background.ANY,
                new InfographicSpec.Shape("splitCard", "surface", "primary"));
        InfographicSelector selector = new InfographicSelector(List.of(splitVisual), "job-1", false);
        assertEquals("split-visual", selector.select(
                NewsletterSection.PROJECT_UPDATES, genericPoints(4)).orElseThrow().name(),
                "SPLIT_VISUAL needs no keyword signal — its own item-count range is the filter");
        InfographicSelector tooMany = new InfographicSelector(List.of(splitVisual), "job-1", false);
        assertTrue(tooMany.select(NewsletterSection.PROJECT_UPDATES, genericPoints(5)).isEmpty(),
                "five points exceed split-visual's maxItems of 4");
    }

    private static InfographicSpec cardGridVariant(String suffix) {
        return new InfographicSpec("cards-" + suffix, InfographicSpec.Archetype.CARD_GRID, 3, 6,
                60, 220, false, InfographicSpec.Background.ANY,
                new InfographicSpec.Shape("cards", "surface", "primary"));
    }
}
