package com.tcs.contentGenerator.agent.design;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.design.Theme;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the two silent-fallback risks in this catalog: an unrecognized
 * default name (the pipeline would compose against the wrong template) and a
 * template JSON missing one of the fixed textStyle/color-role keys
 * {@link com.tcs.contentGenerator.agent.design.layout.LayoutEngine} and the
 * renderers look up by literal name (a miss silently falls back to a
 * hardcoded default style/color instead of failing loudly).
 */
class TemplateCatalogTest {

    private static final Set<String> REQUIRED_TEXT_STYLES = Set.of(
            "IssueTitle", "SectionTitle", "HeroHeadline", "Headline",
            "Body", "Stat", "StatLabel", "EventItem");
    private static final Set<String> REQUIRED_COLOR_ROLES = Set.of(
            "background", "surface", "text", "muted", "primary", "secondary", "accent", "divider");

    private final TemplateCatalog catalog = new TemplateCatalog(JsonMapper.builder().build());

    @Test
    void defaultTemplateIsTcsBrand() {
        assertEquals("tcs-brand", catalog.getDefault().name());
    }

    @Test
    void tdClassicIsStillLoadableByName() {
        assertDoesNotThrow(() -> catalog.get("td-classic"));
        assertEquals("td-classic", catalog.get("td-classic").name());
    }

    @Test
    void unknownTemplateNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> catalog.get("does-not-exist"));
    }

    @Test
    void tcsBrandDefinesAllRequiredTextStyleKeys() {
        Theme theme = catalog.get("tcs-brand").theme();
        assertEquals(REQUIRED_TEXT_STYLES, theme.textStyles().keySet());
    }

    @Test
    void tcsBrandDefinesAllRequiredColorRoles() {
        Theme theme = catalog.get("tcs-brand").theme();
        assertEquals(REQUIRED_COLOR_ROLES, theme.colors().keySet());
    }
}
