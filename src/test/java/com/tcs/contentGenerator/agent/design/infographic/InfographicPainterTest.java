package com.tcs.contentGenerator.agent.design.infographic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.Theme;

/**
 * Every infographic shape must rasterize through Batik (the strictest of the
 * three renderer consumers — same guard as {@code DecorPainterTest}), and the
 * encode→paint round trip must survive, because the asset id is the only
 * channel the drawing params travel through.
 */
class InfographicPainterTest {

    private static final Theme THEME = new Theme(new PageSize(595, 842),
            Map.of("background", "#FFFFFF", "surface", "#EBEBEB", "text", "#000000",
                    "muted", "#404040", "primary", "#4E84C4", "secondary", "#FBB034",
                    "accent", "#54B948", "divider", "#D9D9D9"),
            Map.of(), new Spacing(48, 16));

    @Test
    void numberedBarRowCarriesBarDiscAndNumber() throws Exception {
        String svg = InfographicPainter.numberedBars(THEME, "primary", "text", 3, 500, 48);
        assertTrue(svg.contains("#4E84C4"), "bar carries the resolved primary color");
        assertTrue(svg.contains("<circle"), "numbered disc present");
        assertTrue(svg.contains(">03<"), "two-digit item number painted");
        assertTrue(svg.contains("#FFFFFF"), "number is white on the dark disc");
        BufferedImage image = rasterize(svg);
        assertEquals(500, image.getWidth());
    }

    @Test
    void chevronBarRowCarriesAPointedRightEdgeInsteadOfARoundedOne() throws Exception {
        String rounded = InfographicPainter.numberedBars(THEME, "primary", "text", 1, 500, 48);
        String chevron = InfographicPainter.chevronBars(THEME, "primary", "text", 1, 500, 48);
        assertTrue(rounded.contains("<rect"), "numberedBars bar is a rounded rect");
        assertTrue(chevron.contains("<polygon"), "chevronBars bar is a pointed polygon, not a rect");
        assertTrue(chevron.contains("#4E84C4"), "bar carries the resolved primary color");
        assertTrue(chevron.contains(">01<"), "numbered disc still present");
        BufferedImage image = rasterize(chevron);
        assertEquals(500, image.getWidth());
    }

    @Test
    void pointCardCarriesAShadowedCardAndACornerBadge() throws Exception {
        String svg = InfographicPainter.pointCard(THEME, "surface", "primary", 4, 240, 140);
        assertTrue(svg.contains("feGaussianBlur"), "card must carry the shadow filter");
        assertTrue(svg.contains("#EBEBEB"), "card carries the resolved surface fill");
        assertTrue(svg.contains("<circle") && svg.contains(">04<"), "corner badge with the item number");
        BufferedImage image = rasterize(svg);
        assertEquals(240, image.getWidth());
    }

    @Test
    void timelineNodeCarriesAConnectorLineAndACenteredNode() throws Exception {
        String svg = InfographicPainter.timelineNode(THEME, "divider", "primary", 2, 240, 60);
        assertTrue(svg.contains("<line"), "vertical connector line present");
        assertTrue(svg.contains("#D9D9D9"), "line carries the resolved divider color");
        assertTrue(svg.contains("#4E84C4"), "node carries the resolved primary color");
        assertTrue(svg.contains(">02<"), "two-digit item number painted");
        BufferedImage image = rasterize(svg);
        assertEquals(240, image.getWidth());
    }

    @Test
    void encodePaintRoundTripSurvivesTheAssetId() throws Exception {
        String params = InfographicPainter.encode(
                new InfographicSpec.Shape("numberedBars", "primary", "text"), 2);
        assertEquals("numberedBars.primary.text.2", params);
        String svg = InfographicPainter.paint(params, THEME, 480, 40);
        assertTrue(svg.contains(">02<"));
        rasterize(svg);
    }

    @Test
    void unknownKindPaintsNothingInsteadOfThrowing() {
        assertNull(InfographicPainter.paint("noSuchKind.primary.text.1", THEME, 100, 40),
                "unknown kinds must degrade to the renderers' placeholder path");
    }

    @Test
    void allThreeKindsDispatchThroughPaint() throws Exception {
        rasterize(InfographicPainter.paint("chevronBars.primary.secondary.1", THEME, 480, 40));
        rasterize(InfographicPainter.paint("pointCard.surface.primary.2", THEME, 240, 140));
        rasterize(InfographicPainter.paint("timelineNode.divider.primary.3", THEME, 240, 60));
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
