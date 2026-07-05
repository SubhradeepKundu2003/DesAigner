package com.tcs.contentGenerator.agent.design;

import com.tcs.contentGenerator.design.Theme;

/**
 * A named, JSON-defined design template (theme only, for now — section
 * patterns are chosen by deterministic rules in {@link DesignCompositionAgent}
 * rather than declared per template until a second template needs different
 * ones). Loaded by {@link TemplateCatalog} from {@code resources/design-templates/}.
 */
public record DesignTemplate(String name, Theme theme) {
}
