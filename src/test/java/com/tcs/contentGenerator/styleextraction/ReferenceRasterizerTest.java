package com.tcs.contentGenerator.styleextraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tcs.contentGenerator.styleextraction.ReferenceRasterizer.PageImage;

class ReferenceRasterizerTest {

    @TempDir
    Path tempDir;

    @Test
    void normalPageScalesLongestSideToMaxPx() throws Exception {
        Path pdf = pdfWithPages(tempDir, "a4.pdf", new PDRectangle(595, 842));
        List<PageImage> images = new ReferenceRasterizer(512, 3).rasterize(pdf);

        assertEquals(1, images.size());
        assertEquals("a4.pdf page 1", images.get(0).label());
        BufferedImage decoded = decode(images.get(0));
        assertEquals(512, Math.max(decoded.getWidth(), decoded.getHeight()), 1);
    }

    @Test
    void tallScrollPageIsRenderedAtFullWidthAndSliced() throws Exception {
        // aspect 4:1 — well past the 1.8 slicing threshold (mirrors the staged
        // 595x2526pt email-style reference)
        Path pdf = pdfWithPages(tempDir, "scroll.pdf", new PDRectangle(300, 1200));
        List<PageImage> images = new ReferenceRasterizer(256, 10).rasterize(pdf);

        // width scaled to 256 → full height 1024 → 4 slices of 256
        assertEquals(4, images.size());
        assertTrue(images.get(0).label().contains("part 1/4"), images.get(0).label());
        BufferedImage first = decode(images.get(0));
        assertEquals(256, first.getWidth(), 1);
        assertEquals(256, first.getHeight(), 1);
    }

    @Test
    void maxImagesPerDocumentCapsBothPagesAndSlices() throws Exception {
        Path manyPages = pdfWithPages(tempDir, "many.pdf",
                new PDRectangle(595, 842), new PDRectangle(595, 842),
                new PDRectangle(595, 842), new PDRectangle(595, 842));
        assertEquals(3, new ReferenceRasterizer(128, 3).rasterize(manyPages).size());

        Path tall = pdfWithPages(tempDir, "tall.pdf", new PDRectangle(300, 1200));
        assertEquals(2, new ReferenceRasterizer(256, 2).rasterize(tall).size());
    }

    private static Path pdfWithPages(Path dir, String name, PDRectangle... pageSizes) throws Exception {
        Path file = dir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (PDRectangle size : pageSizes) {
                document.addPage(new PDPage(size));
            }
            document.save(file.toFile());
        }
        return file;
    }

    private static BufferedImage decode(PageImage image) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(image.png()));
    }
}
