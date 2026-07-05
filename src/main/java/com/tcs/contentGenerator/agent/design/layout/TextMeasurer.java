package com.tcs.contentGenerator.agent.design.layout;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.tcs.contentGenerator.design.TextStyle;

/**
 * Estimates how tall a block of text will be once wrapped to a given width,
 * using AWT font metrics. This is a layout estimate only, not a rendering
 * step — every renderer wraps the text natively at final render time, so a
 * slightly-off estimate only costs a bit of extra whitespace or clipping, never
 * incorrect text.
 */
public class TextMeasurer {

    private static final ConcurrentMap<String, Font> FONT_CACHE = new ConcurrentHashMap<>();

    /** Estimated height in points of {@code text} wrapped to {@code boxWidthPt}. */
    public double heightOf(String text, TextStyle style, double boxWidthPt) {
        if (text == null || text.isBlank()) {
            return style.lineHeightPt();
        }
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try {
            FontMetrics metrics = g.getFontMetrics(fontFor(style));
            int lines = 0;
            for (String paragraph : text.split("\n", -1)) {
                lines += wrappedLineCount(paragraph, metrics, boxWidthPt);
            }
            return Math.max(lines, 1) * style.lineHeightPt();
        } finally {
            g.dispose();
        }
    }

    private static int wrappedLineCount(String paragraph, FontMetrics metrics, double boxWidthPt) {
        String trimmed = paragraph.strip();
        if (trimmed.isEmpty()) {
            return 1;
        }
        String[] words = trimmed.split("\\s+");
        double spaceWidth = metrics.stringWidth(" ");
        int lines = 1;
        double lineWidth = 0;
        for (String word : words) {
            double wordWidth = metrics.stringWidth(word);
            double next = lineWidth == 0 ? wordWidth : lineWidth + spaceWidth + wordWidth;
            if (next > boxWidthPt && lineWidth > 0) {
                lines++;
                lineWidth = wordWidth;
            } else {
                lineWidth = next;
            }
        }
        return lines;
    }

    private static Font fontFor(TextStyle style) {
        String key = style.fontFamily() + "-" + style.fontWeight() + "-" + style.fontSizePt();
        return FONT_CACHE.computeIfAbsent(key, k -> {
            int weight = "bold".equalsIgnoreCase(style.fontWeight()) ? Font.BOLD : Font.PLAIN;
            return new Font(style.fontFamily(), weight, (int) Math.round(style.fontSizePt()));
        });
    }
}
