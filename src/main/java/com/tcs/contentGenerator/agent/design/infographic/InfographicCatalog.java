package com.tcs.contentGenerator.agent.design.infographic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Loads infographic specs from {@code resources/infographics/*.json} — the
 * infographic counterpart of {@code TemplateCatalog}. Unlike the template
 * catalog it scans the folder instead of naming files, so growing the design
 * library is "drop a JSON in", no code change. A spec that fails validation
 * fails startup loudly (same contract as an unknown template name): a broken
 * library file is a build-time mistake, not a runtime condition.
 */
@Component
public class InfographicCatalog {

    private final Map<String, InfographicSpec> specs;

    public InfographicCatalog(ObjectMapper objectMapper) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:infographics/*.json");
            this.specs = Stream.of(resources)
                    .map(resource -> load(objectMapper, resource))
                    .collect(Collectors.toUnmodifiableMap(InfographicSpec::name, Function.identity()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan resources/infographics/", e);
        }
    }

    private static InfographicSpec load(ObjectMapper objectMapper, Resource resource) {
        try {
            InfographicSpec spec = objectMapper.readValue(resource.getInputStream(), InfographicSpec.class);
            validate(spec, resource.getFilename());
            return spec;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load infographic spec " + resource.getFilename(), e);
        }
    }

    private static void validate(InfographicSpec spec, String file) {
        if (spec.name() == null || spec.name().isBlank()
                || spec.archetype() == null
                || spec.background() == null
                || spec.shape() == null || spec.shape().kind() == null
                || spec.minItems() < 2 || spec.maxItems() < spec.minItems()
                || spec.titleCapacity() <= 0 || spec.bodyCapacity() < 0) {
            throw new IllegalArgumentException("Invalid infographic spec in " + file + ": " + spec);
        }
    }

    public List<InfographicSpec> all() {
        return List.copyOf(specs.values());
    }

    public InfographicSpec get(String name) {
        InfographicSpec spec = specs.get(name);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown infographic spec: " + name);
        }
        return spec;
    }
}
