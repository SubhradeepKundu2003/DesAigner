package com.tcs.contentGenerator.agent.design.decor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.design.Decor;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.Theme;

/**
 * Every generated decoration must rasterize through Batik — the strictest of
 * the three consumers (a malformed SVG would fail PPTX export at runtime, so
 * it fails here first; same guard pattern as {@code SectionIconAssetsTest}).
 */
class DecorPainterTest {

    private static final Theme THEME = new Theme(new PageSize(595, 842),
            Map.of("background", "#FFFFFF", "surface", "#EBEBEB", "text", "#000000",
                    "muted", "#404040", "primary", "#4E84C4", "secondary", "#FBB034",
                    "accent", "#54B948", "divider", "#D9D9D9"),
            Map.of(), new Spacing(48, 16));

    @Test
    void mastheadWithWaveEdgeRasterizesAndCarriesThemeColors() throws Exception {
        String svg = DecorPainter.masthead(
                new Decor.Masthead("gradient-band", "secondary", "primary", 0, 130, "wave"), THEME, 595, 130);
        assertTrue(svg.contains("#FBB034") && svg.contains("#4E84C4"),
                "gradient stops must carry the resolved theme colors");
        assertTrue(svg.contains("<path"), "wave edge must be a path, not a rect");
        BufferedImage image = rasterize(svg);
        assertEquals(595, image.getWidth());
    }

    @Test
    void flatMastheadIsARectangle() throws Exception {
        String svg = DecorPainter.masthead(
                new Decor.Masthead("gradient-band", "primary", "secondary", 90, 100, "flat"), THEME, 595, 100);
        assertTrue(svg.contains("<rect"), "flat edge must be a rect");
        rasterize(svg);
    }

    @Test
    void chipStatCardAndFooterAllRasterize() throws Exception {
        rasterize(DecorPainter.chip(new Decor.SectionHeader("chip", "primary", false), THEME, 20, 20));
        String card = DecorPainter.statCard(new Decor.StatCard("surface", "secondary", true), THEME, 500, 60);
        assertTrue(card.contains("feGaussianBlur"), "shadowed card must carry the blur filter");
        rasterize(card);
        rasterize(DecorPainter.footer(new Decor.Footer("band", "secondary", "primary"), THEME, 595, 8));
    }

    @Test
    void heroPanelWithQuoteGlyphRasterizes() throws Exception {
        String svg = DecorPainter.heroPanel(new Decor.Hero("surface", "primary"), THEME, 500, 160);
        assertTrue(svg.contains("<text"), "hero panel carries the quote glyph");
        assertTrue(svg.contains("#EBEBEB") && svg.contains("#4E84C4"), "resolved fill + accent colors");
        BufferedImage image = rasterize(svg);
        assertEquals(500, image.getWidth());
    }

    @Test
    void unknownColorRoleFallsBackInsteadOfEmittingNull() throws Exception {
        String svg = DecorPainter.chip(new Decor.SectionHeader("chip", "no-such-role", false), THEME, 20, 20);
        assertTrue(svg.contains("#888888"), "unknown roles must fall back to the neutral gray");
        rasterize(svg);
    }

    private static BufferedImage rasterize(String svg) throws Exception {
        PNGTranscoder transcoder = new PNGTranscoder();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transcoder.transcode(
                new TranscoderInput(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))),
                new TranscoderOutput(out));
        return ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
    }
}
