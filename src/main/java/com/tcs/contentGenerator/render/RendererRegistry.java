package com.tcs.contentGenerator.render;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.design.DesignDocument;

/**
 * Looks up the {@link DesignRenderer} for a requested {@link ExportFormat},
 * the same registry-over-a-list pattern {@code ExtractorRegistry} uses for
 * document ingestion — adding a format is adding a bean, not editing a switch.
 */
@Component
public class RendererRegistry {

    private final Map<ExportFormat, DesignRenderer> renderers;

    public RendererRegistry(List<DesignRenderer> renderers) {
        this.renderers = renderers.stream()
                .collect(Collectors.toMap(DesignRenderer::format, Function.identity()));
    }

    public byte[] render(ExportFormat format, DesignDocument document) {
        DesignRenderer renderer = renderers.get(format);
        if (renderer == null) {
            throw new IllegalStateException("No renderer registered for " + format);
        }
        return renderer.render(document);
    }
}
