package com.tcs.contentGenerator.agent.design.infographic;

/**
 * One infographic design, declared as data in {@code resources/infographics/
 * *.json} (loaded by {@link InfographicCatalog}) — the infographic sibling of
 * {@code DesignTemplate}. A spec describes <em>when the design fits</em>
 * (item-count range, per-item text capacities, background suitability,
 * whether every point must carry a number) and <em>how it is drawn</em>
 * ({@link Shape}: the {@code InfographicPainter} kind plus theme color
 * <em>roles</em> — never literal hex values, so the same design recolors
 * itself under every template). Selection over these specs is
 * {@link InfographicSelector}'s job; geometry stays in {@code LayoutEngine}.
 */
public record InfographicSpec(
        String name,
        Archetype archetype,
        int minItems,
        int maxItems,
        int titleCapacity,
        int bodyCapacity,
        boolean wantsNumbers,
        Background background,
        Shape shape) {

    /**
     * The layout family a design belongs to — what the selection engine
     * reasons about; the concrete spec is a styled variation within one.
     */
    public enum Archetype {
        /** Sequential or ranked points as numbered rows. */
        NUMBERED_LIST,
        /** Parallel, equal-weight categories as a grid of cards. */
        CARD_GRID,
        /** Points forming a loop or facets of one theme (donut/fan). */
        CYCLE,
        /** Steps, phases, journey, chronology. */
        TIMELINE,
        /** One central concept with supporting points around it. */
        HUB_SPOKE,
        /** Every point carries a number/percentage. */
        KPI_BARS,
        /** Prose beside a compact visual of 3-4 sub-points. */
        SPLIT_VISUAL
    }

    /** Which page backgrounds the design reads well on. */
    public enum Background {
        LIGHT, DARK, ANY
    }

    /**
     * Drawing parameters for {@code InfographicPainter}. {@code kind} selects
     * the painter; the color fields name theme roles. Fields not used by a
     * kind stay null (the {@code Decor} nullable-fields convention).
     */
    public record Shape(String kind, String barFill, String leadFill) {
    }
}
