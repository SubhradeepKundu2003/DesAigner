package com.tcs.contentGenerator.render.font;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Optional brand-font loader for "Houschka Rounded". Bundling the actual
 * TTF/OTF files is not required — if they are absent, every method here is a
 * clean no-op and every renderer already falls back to its logical
 * {@code fontFamily} handling exactly as it did before this class existed.
 * Drop the two files in at {@code src/main/resources/fonts/} (same "editors
 * add files, no config or code change needed" convention as
 * {@code AssetLibrary}) and they activate automatically on next restart —
 * no code change required.
 */
@Component
public class BrandFontRegistry {

    private static final Logger log = LoggerFactory.getLogger(BrandFontRegistry.class);
    private static final String FAMILY = "Houschka Rounded";
    private static final Map<String, List<String>> CANDIDATE_FILES = Map.of(
            "normal", List.of(
                    "fonts/houschka-rounded-regular.ttf",
                    "fonts/HouschkaRoundedAlt-Medium.ttf"),
            "bold", List.of(
                    "fonts/houschka-rounded-bold.ttf",
                    "fonts/HouschkaRoundedAlt-DemiBold.ttf"));

    private final Map<String, byte[]> bytesByWeight = new HashMap<>();

    public BrandFontRegistry() {
        CANDIDATE_FILES.forEach((weight, candidates) -> {
            for (String path : candidates) {
                ClassPathResource resource = new ClassPathResource(path);
                if (!resource.exists()) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    byte[] bytes = in.readAllBytes();
                    Font awtFont = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(awtFont);
                    bytesByWeight.put(weight, bytes);
                    log.info("Loaded brand font {} ({})", FAMILY, path);
                    break;
                } catch (IOException | FontFormatException e) {
                    log.warn("Could not load brand font {}; falling back to logical font family", path, e);
                }
            }
        });
    }

    /** The brand font's family name — matches the {@code fontFamily} used in {@code tcs-brand.json}. */
    public String family() {
        return FAMILY;
    }

    /** Font bytes for the given weight ("bold" or anything else = normal), if that file was found at startup. */
    public Optional<byte[]> bytesFor(String weight) {
        return Optional.ofNullable(bytesByWeight.get("bold".equalsIgnoreCase(weight) ? "bold" : "normal"));
    }
}
