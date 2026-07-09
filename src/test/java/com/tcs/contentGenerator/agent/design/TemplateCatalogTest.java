package com.tcs.contentGenerator.agent.design;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final Set<String> ALL_TEMPLATES = Set.of("tcs-brand", "td-classic", "noir-luxe");
    private static final Set<String> REQUIRED_TEXT_STYLES = Set.of(
            "IssueTitle", "SectionTitle", "HeroHeadline", "Headline",
            "Body", "Stat", "StatLabel", "EventItem");
    private static final Set<String> REQUIRED_COLOR_ROLES = Set.of(
            "background", "surface", "text", "muted", "primary", "secondary", "accent", "divider");

    private final TemplateCatalog catalog = new TemplateCatalog(JsonMapper.builder().build(), "tcs-brand");

    @Test
    void defaultTemplateIsTcsBrand() {
        assertEquals("tcs-brand", catalog.getDefault().name());
    }

    @Test
    void everyTemplateIsLoadableByName() {
        for (String name : ALL_TEMPLATES) {
            assertDoesNotThrow(() -> catalog.get(name));
            assertEquals(name, catalog.get(name).name());
        }
    }

    @Test
    void defaultIsConfigurable() {
        TemplateCatalog noirDefault = new TemplateCatalog(JsonMapper.builder().build(), "noir-luxe");
        assertEquals("noir-luxe", noirDefault.getDefault().name());
    }

    @Test
    void unknownConfiguredDefaultFailsAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateCatalog(JsonMapper.builder().build(), "does-not-exist"));
    }

    @Test
    void unknownTemplateNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> catalog.get("does-not-exist"));
    }

    @Test
    void everyTemplateDefinesAllRequiredTextStyleKeys() {
        for (String name : ALL_TEMPLATES) {
            Theme theme = catalog.get(name).theme();
            // containsAll, not equals: templates may add optional styles
            // (e.g. IssueTitleOnBand for the decor masthead)
            assertTrue(theme.textStyles().keySet().containsAll(REQUIRED_TEXT_STYLES),
                    name + " is missing required styles");
        }
    }

    @Test
    void everyTemplateDefinesAllRequiredColorRoles() {
        for (String name : ALL_TEMPLATES) {
            Theme theme = catalog.get(name).theme();
            assertEquals(REQUIRED_COLOR_ROLES, theme.colors().keySet(), name);
        }
    }
}
