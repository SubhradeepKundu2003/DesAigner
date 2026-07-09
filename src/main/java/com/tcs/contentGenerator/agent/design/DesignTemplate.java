package com.tcs.contentGenerator.agent.design;

import com.tcs.contentGenerator.design.Theme;

/**
 * A named, JSON-defined design template: theme plus an optional {@link Decor}
 * (null = plain, pre-decor output — section patterns are still chosen by
 * deterministic rules in {@link DesignCompositionAgent} rather than declared
 * per template until a second template needs different ones). Loaded by
 * {@link TemplateCatalog} from {@code resources/design-templates/}.
 */
public record DesignTemplate(String name, Theme theme, Decor decor) {

    /** Decor-less template — keeps existing callers/fixtures unchanged. */
    public DesignTemplate(String name, Theme theme) {
        this(name, theme, null);
    }
}
