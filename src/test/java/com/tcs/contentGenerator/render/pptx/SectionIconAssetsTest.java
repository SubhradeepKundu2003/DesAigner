package com.tcs.contentGenerator.render.pptx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.planning.NewsletterSection;

/**
 * Guards the bundled default section icons at {@code storage/assets/ICONS/}:
 * every {@link NewsletterSection} must have one, and each must survive Batik
 * rasterization — the strictest consumer ({@code PptxDesignRenderer}
 * rasterizes SVG to PNG at export time; a malformed SVG would fail the whole
 * PPTX export at runtime, so it should fail here first).
 */
class SectionIconAssetsTest {

    private static final Path ICONS_DIR = Path.of("storage", "assets", "ICONS");

    @Test
    void everySectionHasAnIconFile() {
        for (NewsletterSection section : NewsletterSection.values()) {
            assertTrue(Files.isRegularFile(ICONS_DIR.resolve(section.name() + ".svg")),
                    "expected a bundled icon for " + section.name());
        }
    }

    @Test
    void everyBundledIconRasterizesThroughBatik() throws Exception {
        for (NewsletterSection section : NewsletterSection.values()) {
            Path svg = ICONS_DIR.resolve(section.name() + ".svg");
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 48f);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 48f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var in = Files.newInputStream(svg)) {
                transcoder.transcode(new TranscoderInput(in), new TranscoderOutput(out));
            }
            assertTrue(out.size() > 0, section.name() + ".svg produced an empty PNG");
        }
    }
}
