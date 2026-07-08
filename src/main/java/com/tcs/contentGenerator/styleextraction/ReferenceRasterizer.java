package com.tcs.contentGenerator.styleextraction;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Renders reference-PDF pages to PNG images sized for the vision model. Normal
 * pages are scaled so their longest side is {@code app.style-extraction.max-image-px}
 * (bigger buys nothing — the model downsamples internally — and costs context
 * tokens). Very tall pages (scroll/email-style newsletters, e.g. the staged
 * 595×2526pt reference) would become unreadably small if scaled whole, so they
 * are rendered at full width and sliced vertically into square-ish segments
 * instead. {@code max-images-per-document} caps the total either way, so one
 * long reference can't blow the (CPU-bound) vision-call budget.
 */
@Component
public class ReferenceRasterizer {

    /** height/width beyond which a page is sliced rather than scaled whole. */
    private static final double TALL_ASPECT = 1.8;

    private final int maxImagePx;
    private final int maxImagesPerDocument;

    public ReferenceRasterizer(
            @Value("${app.style-extraction.max-image-px:1024}") int maxImagePx,
            @Value("${app.style-extraction.max-images-per-document:3}") int maxImagesPerDocument) {
        this.maxImagePx = maxImagePx;
        this.maxImagesPerDocument = maxImagesPerDocument;
    }

    /** One rasterized page (or slice of a tall page), PNG-encoded, with a human-readable label. */
    public record PageImage(String label, byte[] png) {
    }

    public List<PageImage> rasterize(Path pdf) {
        String fileName = pdf.getFileName().toString();
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<PageImage> images = new ArrayList<>();
            for (int page = 0; page < document.getNumberOfPages() && images.size() < maxImagesPerDocument; page++) {
                PDRectangle box = document.getPage(page).getMediaBox();
                String pageLabel = fileName + " page " + (page + 1);
                if (box.getHeight() / box.getWidth() <= TALL_ASPECT) {
                    // PDFRenderer scale 1.0 = 72 dpi = 1px per pt, so this puts the
                    // longest side at maxImagePx.
                    float scale = maxImagePx / Math.max(box.getWidth(), box.getHeight());
                    images.add(new PageImage(pageLabel, encodePng(renderer.renderImage(page, scale))));
                } else {
                    sliceTallPage(renderer, page, box, pageLabel, images);
                }
            }
            return images;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to rasterize reference PDF " + fileName, e);
        }
    }

    private void sliceTallPage(PDFRenderer renderer, int page, PDRectangle box, String pageLabel,
            List<PageImage> images) throws IOException {
        BufferedImage full = renderer.renderImage(page, maxImagePx / box.getWidth());
        int sliceCount = (int) Math.ceil(full.getHeight() / (double) maxImagePx);
        for (int slice = 0; slice < sliceCount && images.size() < maxImagesPerDocument; slice++) {
            int top = slice * maxImagePx;
            int height = Math.min(maxImagePx, full.getHeight() - top);
            String label = pageLabel + " (part " + (slice + 1) + "/" + sliceCount + ", top to bottom)";
            images.add(new PageImage(label, encodePng(full.getSubimage(0, top, full.getWidth(), height))));
        }
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
