package com.tcs.contentGenerator.agent.design;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Loads design templates from {@code resources/design-templates/*.json} —
 * editable without recompiling, later user-uploadable. {@code tcs-brand} is
 * the active default; {@code td-classic} is kept loadable via {@link #get}
 * for comparison/rollback.
 */
@Component
public class TemplateCatalog {

    private static final String DEFAULT_NAME = "tcs-brand";

    private final Map<String, DesignTemplate> templates;

    public TemplateCatalog(ObjectMapper objectMapper) {
        DesignTemplate classic = load(objectMapper, "design-templates/td-classic.json");
        DesignTemplate tcsBrand = load(objectMapper, "design-templates/tcs-brand.json");
        this.templates = Map.of(classic.name(), classic, tcsBrand.name(), tcsBrand);
    }

    private static DesignTemplate load(ObjectMapper objectMapper, String classpathLocation) {
        try {
            return objectMapper.readValue(
                    new ClassPathResource(classpathLocation).getInputStream(), DesignTemplate.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load design template " + classpathLocation, e);
        }
    }

    public DesignTemplate getDefault() {
        return templates.get(DEFAULT_NAME);
    }

    public DesignTemplate get(String name) {
        DesignTemplate template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("Unknown design template: " + name);
        }
        return template;
    }
}
