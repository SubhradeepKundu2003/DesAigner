package com.tcs.contentGenerator.render.pptx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.junit.jupiter.api.Test;

/**
 * Guards the bundled starter set of per-point infographic icons at
 * {@code storage/assets/ICONS/} (the {@code IconMatcher.matchPointIcon}
 * keyword pool — same folder as the section icons, distinguished by not
 * being a {@code NewsletterSection} name). Mirrors {@link SectionIconAssetsTest}:
 * every keyword's file must exist and survive Batik rasterization, the
 * strictest consumer at PPTX export time.
 */
class PointIconAssetsTest {

    private static final Path ICONS_DIR = Path.of("storage", "assets", "ICONS");
    private static final List<String> STARTER_KEYWORDS = List.of(
            "growth", "security", "team", "award", "milestone", "launch", "deadline", "deployment");

    @Test
    void everyStarterKeywordHasAnIconFile() {
        for (String keyword : STARTER_KEYWORDS) {
            assertTrue(Files.isRegularFile(ICONS_DIR.resolve(keyword + ".svg")),
                    "expected a bundled point icon for " + keyword);
        }
    }

    @Test
    void everyBundledPointIconRasterizesThroughBatik() throws Exception {
        for (String keyword : STARTER_KEYWORDS) {
            Path svg = ICONS_DIR.resolve(keyword + ".svg");
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 48f);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 48f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var in = Files.newInputStream(svg)) {
                transcoder.transcode(new TranscoderInput(in), new TranscoderOutput(out));
            }
            assertTrue(out.size() > 0, keyword + ".svg produced an empty PNG");
        }
    }
}
