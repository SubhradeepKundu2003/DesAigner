package com.tcs.contentGenerator.agent.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import org.junit.jupiter.api.Test;

class IconMatcherTest {

    @Test
    void matchesAKeywordThatAppearsAsAWholeWordInTheLabel() {
        assertEquals("growth", IconMatcher.matchPointIcon("Revenue growth accelerates",
                Set.of("growth", "security", "team")));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals("growth", IconMatcher.matchPointIcon("REVENUE GROWTH", Set.of("growth")));
    }

    @Test
    void requiresAWholeWordNotASubstring() {
        // "growth" must not match inside "growthful" or similar run-on text
        assertNull(IconMatcher.matchPointIcon("Outgrowthful metrics", Set.of("growth")));
    }

    @Test
    void returnsNullWhenNoKeywordMatches() {
        assertNull(IconMatcher.matchPointIcon("Discovery phase complete", Set.of("growth", "security")));
    }

    @Test
    void prefersTheLongerKeywordWhenSeveralMatch() {
        // "leadership" is both a whole-word match on its own and contains "lead" as a separate concept;
        // set up two real whole-word matches and confirm the longer one wins
        assertEquals("team growth", IconMatcher.matchPointIcon("Team growth this quarter",
                Set.of("growth", "team growth")));
    }

    @Test
    void assetIdForPointCarriesTheKeywordUnderTheWellKnownPrefix() {
        assertEquals("icon-point-growth", IconMatcher.assetIdForPoint("growth"));
        assertEquals(IconMatcher.POINT_ICON_ASSET_PREFIX + "growth", IconMatcher.assetIdForPoint("growth"));
    }
}
