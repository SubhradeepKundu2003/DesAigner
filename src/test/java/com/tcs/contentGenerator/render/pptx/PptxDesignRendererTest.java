package com.tcs.contentGenerator.render.pptx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.ShapeBox;
import com.tcs.contentGenerator.design.SourceLink;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.render.ExportFormat;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Verifies the PPTX renderer against the same kind of fixture
 * {@code DesignDocumentSerializationTest} uses, but reads the output back
 * through POI to assert on actual PowerPoint shapes rather than just bytes.
 */
class PptxDesignRendererTest {

    private static final Theme THEME = new Theme(
            new PageSize(595, 842),
            Map.of("primary", "#0B5FFF", "surface", "#F4F4F4", "divider", "#D8DEE8", "muted", "#5B6470"),
            Map.of("Headline", new TextStyle("SansSerif", 13, "bold", "primary", 17)),
            new Spacing(48, 16));

    @Test
    void reportsPptxFormat() {
        PptxDesignRenderer renderer = new PptxDesignRenderer(new FakeStorageService(Map.of()));
        assertEquals(ExportFormat.PPTX, renderer.format());
    }

    @Test
    void rendersOnePageAsOneSlideWithAllComponentShapes() throws Exception {
        SourceLink source = new SourceLink("Delivery Highlights", "NPS climbs to 72");
        TextBox headline = new TextBox("cmp-1", ComponentRole.ARTICLE_HEADLINE,
                new Frame(48, 48, 300, 20), 0, false, source, "Headline", "NPS climbs to 72");
        ShapeBox icon = new ShapeBox("cmp-2", ComponentRole.SECTION_ICON,
                new Frame(48, 80, 12, 12), 1, false, null, "circle", "primary");
        ShapeBox divider = new ShapeBox("cmp-3", ComponentRole.DIVIDER,
                new Frame(48, 100, 500, 2), 2, false, null, "rect", "divider");
        ImageBox noAsset = new ImageBox("cmp-4", ComponentRole.IMAGE_PLACEHOLDER,
                new Frame(48, 110, 200, 120), 3, false, null, null, "Team photo");

        byte[] png = tinyPng();
        Asset asset = new Asset("asset-1", "image", "jobs/job-1/team.png", 2, 2);
        ImageBox withAsset = new ImageBox("cmp-5", ComponentRole.IMAGE_PLACEHOLDER,
                new Frame(260, 110, 200, 120), 4, false, null, "asset-1", "Team photo");

        Page page = new Page("page-1", List.of(headline, icon, divider, noAsset, withAsset));
        DesignDocument document = new DesignDocument(1, 1,
                new DesignMeta("TD Monthly — July 2026", "job-1"), THEME, List.of(asset), List.of(page));

        PptxDesignRenderer renderer = new PptxDesignRenderer(
                new FakeStorageService(Map.of("jobs/job-1/team.png", png)));
        byte[] pptxBytes = renderer.render(document);

        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(pptxBytes))) {
            assertEquals(595, ppt.getPageSize().width);
            assertEquals(842, ppt.getPageSize().height);
            assertEquals("TD Monthly — July 2026", ppt.getProperties().getCoreProperties().getTitle());

            assertEquals(1, ppt.getSlides().size());
            XSLFSlide slide = ppt.getSlides().get(0);
            List<XSLFShape> shapes = slide.getShapes();
            assertEquals(5, shapes.size());

            XSLFTextBox textShape = assertInstanceOf(XSLFTextBox.class, shapes.get(0));
            assertEquals("NPS climbs to 72", textShape.getText());

            XSLFAutoShape iconShape = assertInstanceOf(XSLFAutoShape.class, shapes.get(1));
            assertEquals(ShapeType.ELLIPSE, iconShape.getShapeType());

            XSLFAutoShape dividerShape = assertInstanceOf(XSLFAutoShape.class, shapes.get(2));
            assertEquals(ShapeType.RECT, dividerShape.getShapeType());

            XSLFAutoShape placeholderShape = assertInstanceOf(XSLFAutoShape.class, shapes.get(3));
            assertTrue(placeholderShape.getText().contains("Team photo"));

            XSLFPictureShape pictureShape = assertInstanceOf(XSLFPictureShape.class, shapes.get(4));
            assertArrayEquals(png, pictureShape.getPictureData().getData());
        }
    }

    @Test
    void fallsBackToPlaceholderWhenAssetCannotBeRetrieved() throws Exception {
        Asset asset = new Asset("asset-1", "image", "missing.png", null, null);
        ImageBox brokenAsset = new ImageBox("cmp-1", ComponentRole.IMAGE_PLACEHOLDER,
                new Frame(48, 48, 200, 120), 0, false, null, "asset-1", "Team photo");
        Page page = new Page("page-1", List.of(brokenAsset));
        DesignDocument document = new DesignDocument(1, 1,
                new DesignMeta("Issue", "job-1"), THEME, List.of(asset), List.of(page));

        PptxDesignRenderer renderer = new PptxDesignRenderer(new FailingStorageService());
        byte[] pptxBytes = renderer.render(document);

        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(pptxBytes))) {
            List<XSLFShape> shapes = ppt.getSlides().get(0).getShapes();
            assertEquals(1, shapes.size());
            assertInstanceOf(XSLFAutoShape.class, shapes.get(0));
        }
    }

    private static byte[] tinyPng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private record FakeStorageService(Map<String, byte[]> content) implements StorageService {
        @Override
        public String store(String relativePath, byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] retrieve(String ref) {
            byte[] bytes = content.get(ref);
            if (bytes == null) {
                throw new IllegalStateException("No content for ref " + ref);
            }
            return bytes;
        }

        @Override
        public Path resolve(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String ref) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FailingStorageService implements StorageService {
        @Override
        public String store(String relativePath, byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] retrieve(String ref) {
            throw new UncheckedIOException(new IOException("simulated storage failure"));
        }

        @Override
        public Path resolve(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String ref) {
            throw new UnsupportedOperationException();
        }
    }
}
