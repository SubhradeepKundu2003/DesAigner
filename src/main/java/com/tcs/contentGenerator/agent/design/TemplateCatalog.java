package com.tcs.contentGenerator.agent.design;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Loads design templates from {@code resources/design-templates/*.json} —
 * editable without recompiling, later user-uploadable. The active default is
 * chosen by {@code app.design.template} ({@code tcs-brand} out of the box);
 * every other template stays loadable by name via {@link #get}.
 * {@code noir-luxe} was extracted from the reference PDFs by the style
 * extraction pipeline (dark background — the composition agent picks the
 * white logo variant and dot icons for it).
 */
@Component
public class TemplateCatalog {

    private final Map<String, DesignTemplate> templates;
    private final String defaultName;

    public TemplateCatalog(ObjectMapper objectMapper,
            @Value("${app.design.template:tcs-brand}") String defaultName) {
        this.templates = Stream.of("td-classic", "tcs-brand", "noir-luxe")
                .map(name -> load(objectMapper, "design-templates/" + name + ".json"))
                .collect(Collectors.toUnmodifiableMap(DesignTemplate::name, Function.identity()));
        if (!templates.containsKey(defaultName)) {
            throw new IllegalArgumentException("app.design.template names an unknown template: "
                    + defaultName + " (known: " + templates.keySet() + ")");
        }
        this.defaultName = defaultName;
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
        return templates.get(defaultName);
    }

    public DesignTemplate get(String name) {
        DesignTemplate template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("Unknown design template: " + name);
        }
        return template;
    }
}
