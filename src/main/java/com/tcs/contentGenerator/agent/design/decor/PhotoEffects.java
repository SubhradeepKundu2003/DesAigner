package com.tcs.contentGenerator.agent.design.decor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.tcs.contentGenerator.agent.design.Decor;

/**
 * Java2D photo treatment: center-crop to the target aspect ("crop-to-fill"),
 * clip to an ellipse/rounded rectangle, and paint a soft drop shadow — all
 * baked into one PNG so every renderer shows the identical treated photo
 * (neither openhtmltopdf nor POI can clip or shadow an image natively).
 * The shadow lives <em>inside</em> the output canvas (the photo is inset by a
 * margin), so the caller's frame math needs no change.
 */
public final class PhotoEffects {

    /** Photo inset that makes room for the shadow, as a fraction of the short side. */
    private static final double SHADOW_MARGIN_FRACTION = 0.045;
    /** Must exceed offset + blur reach, or the shadow gets clipped at the canvas edge. */
    private static final int SHADOW_MARGIN_MIN_PX = 10;
    private static final float SHADOW_ALPHA = 0.35f;
    private static final int BLUR_RADIUS_PX = 4;

    private PhotoEffects() {
    }

    /**
     * @param cornerRadiusPx used only when {@code spec.clip()} is "rounded"
     * @throws IOException if the source bytes are not a decodable image
     */
    public static byte[] treat(byte[] sourceBytes, int outWidth, int outHeight,
            Decor.Photo spec, double cornerRadiusPx) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        if (source == null) {
            throw new IOException("Source bytes are not a decodable image");
        }

        int margin = spec.shadow()
                ? Math.max(SHADOW_MARGIN_MIN_PX, (int) (Math.min(outWidth, outHeight) * SHADOW_MARGIN_FRACTION))
                : 0;
        double photoX = margin;
        double photoY = margin;
        double photoW = outWidth - 2.0 * margin;
        double photoH = outHeight - 2.0 * margin;
        Shape clip = clipShape(spec.clip(), photoX, photoY, photoW, photoH, cornerRadiusPx);

        BufferedImage out = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (spec.shadow()) {
                g.drawImage(shadowLayer(outWidth, outHeight, clip, margin / 2.0), 0, 0, null);
            }
            g.setClip(clip);
            drawCropToFill(g, source, photoX, photoY, photoW, photoH);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bytes);
        return bytes.toByteArray();
    }

    private static Shape clipShape(String clip, double x, double y, double w, double h, double radiusPx) {
        return switch (clip == null ? "" : clip) {
            case "ellipse" -> new Ellipse2D.Double(x, y, w, h);
            case "rounded" -> new RoundRectangle2D.Double(x, y, w, h, radiusPx * 2, radiusPx * 2);
            default -> new Rectangle2D.Double(x, y, w, h);
        };
    }

    /** Scales the source to cover the target rect and centers the overflow (object-fit: cover). */
    private static void drawCropToFill(Graphics2D g, BufferedImage source,
            double x, double y, double w, double h) {
        double scale = Math.max(w / source.getWidth(), h / source.getHeight());
        double cropW = w / scale;
        double cropH = h / scale;
        int sx = (int) ((source.getWidth() - cropW) / 2);
        int sy = (int) ((source.getHeight() - cropH) / 2);
        g.drawImage(source,
                (int) x, (int) y, (int) (x + w), (int) (y + h),
                sx, sy, (int) (sx + cropW), (int) (sy + cropH), null);
    }

    /** The clip shape, offset downward, filled translucent black, box-blurred twice (≈ gaussian). */
    private static BufferedImage shadowLayer(int width, int height, Shape clip, double offsetY) {
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0f, 0f, 0f, SHADOW_ALPHA));
            g.translate(0, offsetY);
            g.fill(clip);
        } finally {
            g.dispose();
        }
        ConvolveOp blur = boxBlur();
        return blur.filter(blur.filter(mask, null), null);
    }

    private static ConvolveOp boxBlur() {
        int size = BLUR_RADIUS_PX * 2 + 1;
        float[] weights = new float[size * size];
        java.util.Arrays.fill(weights, 1f / weights.length);
        return new ConvolveOp(new Kernel(size, size, weights), ConvolveOp.EDGE_NO_OP, null);
    }
}
