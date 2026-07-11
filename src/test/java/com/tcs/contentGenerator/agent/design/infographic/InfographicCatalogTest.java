package com.tcs.contentGenerator.agent.design.infographic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * The spec library must load from {@code resources/infographics/} at startup
 * and reject malformed entries loudly — a broken library file is a build-time
 * mistake, not something to discover on a live run.
 */
class InfographicCatalogTest {

    private static final InfographicCatalog CATALOG =
            new InfographicCatalog(JsonMapper.builder().build());

    @Test
    void loadsTheNumberedBarsSpecWithItsFitConstraints() {
        InfographicSpec spec = CATALOG.get("numbered-bars");
        assertEquals(InfographicSpec.Archetype.NUMBERED_LIST, spec.archetype());
        assertEquals(3, spec.minItems());
        assertEquals(5, spec.maxItems());
        assertEquals(InfographicSpec.Background.ANY, spec.background());
        assertFalse(spec.wantsNumbers());
        assertEquals("numberedBars", spec.shape().kind());
        assertTrue(spec.titleCapacity() > 0 && spec.bodyCapacity() > 0,
                "capacities drive the selector's coarse text filter");
    }

    @Test
    void everyLoadedSpecIsListed() {
        assertFalse(CATALOG.all().isEmpty(), "the library ships at least one design");
        assertTrue(CATALOG.all().stream().anyMatch(s -> s.name().equals("numbered-bars")));
        assertTrue(CATALOG.all().stream().anyMatch(s -> s.name().equals("card-grid")));
        assertTrue(CATALOG.all().stream().anyMatch(s -> s.name().equals("kpi-bars")));
        assertTrue(CATALOG.all().stream().anyMatch(s -> s.name().equals("timeline")));
    }

    @Test
    void loadsTheCardGridSpec() {
        InfographicSpec spec = CATALOG.get("card-grid");
        assertEquals(InfographicSpec.Archetype.CARD_GRID, spec.archetype());
        assertEquals("pointCard", spec.shape().kind());
        assertEquals(6, spec.maxItems());
    }

    @Test
    void loadsTheKpiBarsSpecWantingNumbers() {
        InfographicSpec spec = CATALOG.get("kpi-bars");
        assertEquals(InfographicSpec.Archetype.KPI_BARS, spec.archetype());
        assertTrue(spec.wantsNumbers(), "KPI_BARS must require every point to carry a figure");
        assertEquals("chevronBars", spec.shape().kind());
    }

    @Test
    void loadsTheTimelineSpec() {
        InfographicSpec spec = CATALOG.get("timeline");
        assertEquals(InfographicSpec.Archetype.TIMELINE, spec.archetype());
        assertEquals("timelineNode", spec.shape().kind());
        assertFalse(spec.wantsNumbers());
    }

    @Test
    void unknownSpecNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> CATALOG.get("no-such-design"));
    }
}
