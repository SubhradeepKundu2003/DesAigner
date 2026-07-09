package com.tcs.contentGenerator.agent.design.decor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.design.Decor;

class PhotoEffectsTest {

    @Test
    void outputHasExactRequestedDimensions() throws Exception {
        byte[] out = PhotoEffects.treat(solidPng(300, 200, Color.BLUE), 200, 120,
                new Decor.Photo("rounded", 12, true), 24);
        BufferedImage image = decode(out);
        assertEquals(200, image.getWidth());
        assertEquals(120, image.getHeight());
    }

    @Test
    void ellipseClipLeavesCornersTransparent() throws Exception {
        byte[] out = PhotoEffects.treat(solidPng(300, 200, Color.BLUE), 200, 120,
                new Decor.Photo("ellipse", 0, false), 0);
        BufferedImage image = decode(out);
        assertEquals(0, alphaAt(image, 1, 1), "top-left corner must be transparent outside the ellipse");
        assertEquals(0, alphaAt(image, 198, 1), "top-right corner must be transparent outside the ellipse");
        assertTrue(alphaAt(image, 100, 60) > 200, "the ellipse center must be opaque photo");
    }

    @Test
    void shadowPaintsPixelsOutsideThePhotoShape() throws Exception {
        byte[] out = PhotoEffects.treat(solidPng(300, 200, Color.BLUE), 200, 120,
                new Decor.Photo("ellipse", 0, true), 0);
        BufferedImage image = decode(out);
        // straight below the ellipse's bottom edge: outside the photo, inside the shadow
        int shadowAlpha = alphaAt(image, 100, 112);
        assertTrue(shadowAlpha > 0 && shadowAlpha < 255,
                "expected translucent shadow below the photo, alpha was " + shadowAlpha);
    }

    @Test
    void cropToFillCentersTheOverflowingAxis() throws Exception {
        // left half red, right half blue; landscape source into a square target
        BufferedImage source = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 200);
        g.setColor(Color.BLUE);
        g.fillRect(200, 0, 200, 200);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);

        byte[] out = PhotoEffects.treat(bytes.toByteArray(), 100, 100,
                new Decor.Photo("none", 0, false), 0);
        BufferedImage image = decode(out);
        // a centered crop of the middle keeps the red|blue seam at the horizontal center
        assertEquals(Color.RED.getRGB(), image.getRGB(25, 50));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(75, 50));
    }

    @Test
    void undecodableSourceThrowsIoException() {
        assertThrows(IOException.class, () -> PhotoEffects.treat(new byte[] {1, 2, 3}, 100, 100,
                new Decor.Photo("none", 0, false), 0));
    }

    private static byte[] solidPng(int w, int h, Color color) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
