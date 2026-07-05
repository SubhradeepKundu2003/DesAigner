package com.tcs.contentGenerator.render.pptx;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.sl.usermodel.Insets2D;
import org.apache.poi.sl.usermodel.PictureData.PictureType;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.StrokeStyle.LineDash;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.ShapeBox;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.render.DesignRenderer;
import com.tcs.contentGenerator.render.ExportFormat;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Renders a {@link DesignDocument} as a native, PowerPoint-editable PPTX via
 * POI XSLF: one slide per {@link Page}, custom slide size equal to the theme's
 * page size. XSLF shape anchors and {@code Dimension} page size are expressed
 * in points, the same unit our model already uses — no manual pt→EMU math
 * needed, POI does that conversion internally. {@code TextBox} becomes a text
 * shape, {@code ShapeBox} an auto shape (rect/circle), and {@code ImageBox} a
 * picture when its asset resolves, or a dashed placeholder box (matching the
 * HTML renderer) when it doesn't — Agent #8 hasn't been built yet, so every
 * {@code ImageBox} today takes the placeholder path.
 */
@Component
public class PptxDesignRenderer implements DesignRenderer {

    private static final Logger log = LoggerFactory.getLogger(PptxDesignRenderer.class);
    private static final TextStyle DEFAULT_STYLE = new TextStyle("SansSerif", 10, "normal", "text", 14);

    private final StorageService storageService;

    public PptxDesignRenderer(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public ExportFormat format() {
        return ExportFormat.PPTX;
    }

    @Override
    public byte[] render(DesignDocument document) {
        Theme theme = document.theme();
        Map<String, Asset> assetsById = document.assets().stream()
                .collect(Collectors.toMap(Asset::id, Function.identity()));
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new Dimension(
                    (int) Math.round(theme.pageSize().widthPt()),
                    (int) Math.round(theme.pageSize().heightPt())));
            ppt.getProperties().getCoreProperties().setTitle(document.meta().issueTitle());

            for (Page page : document.pages()) {
                XSLFSlide slide = ppt.createSlide();
                for (var component : page.components()) {
                    switch (component) {
                        case TextBox t -> renderText(slide, t, theme);
                        case ShapeBox s -> renderShape(slide, s, theme);
                        case ImageBox i -> renderImage(ppt, slide, i, theme, assetsById);
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render PPTX", e);
        }
    }

    private void renderText(XSLFSlide slide, TextBox box, Theme theme) {
        TextStyle style = theme.textStyles().getOrDefault(box.styleRef(), DEFAULT_STYLE);
        XSLFTextBox shape = slide.createTextBox();
        shape.setAnchor(rectangle(box.frame()));
        shape.setInsets(new Insets(0, 0, 0, 0));
        shape.setWordWrap(true);
        shape.clearText();

        String text = box.text() == null ? "" : box.text();
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            XSLFTextParagraph paragraph = shape.addNewTextParagraph();
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(line);
            applyStyle(run, style, theme);
        }
    }

    private void applyStyle(XSLFTextRun run, TextStyle style, Theme theme) {
        run.setFontFamily(style.fontFamily());
        run.setFontSize(style.fontSizePt());
        run.setBold("bold".equalsIgnoreCase(style.fontWeight()));
        run.setFontColor(colorOf(theme, style.colorRole(), Color.BLACK));
    }

    private void renderShape(XSLFSlide slide, ShapeBox box, Theme theme) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType("circle".equals(box.shapeType()) ? ShapeType.ELLIPSE : ShapeType.RECT);
        shape.setAnchor(rectangle(box.frame()));
        shape.setFillColor(colorOf(theme, box.fillColorRole(), new Color(0xCC, 0xCC, 0xCC)));
        shape.setLineColor(null);
    }

    private void renderImage(XMLSlideShow ppt, XSLFSlide slide, ImageBox box, Theme theme,
            Map<String, Asset> assetsById) {
        Asset asset = box.assetId() == null ? null : assetsById.get(box.assetId());
        if (asset != null) {
            try {
                byte[] bytes = storageService.retrieve(asset.storedRef());
                var pictureData = ppt.addPicture(bytes, pictureTypeOf(asset));
                XSLFPictureShape picture = slide.createPicture(pictureData);
                picture.setAnchor(rectangle(box.frame()));
                return;
            } catch (RuntimeException e) {
                log.warn("Could not embed asset {} ({}) for image box {}; using placeholder",
                        asset.id(), asset.storedRef(), box.id(), e);
            }
        }
        renderPlaceholder(slide, box, theme);
    }

    private void renderPlaceholder(XSLFSlide slide, ImageBox box, Theme theme) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(ShapeType.RECT);
        shape.setAnchor(rectangle(box.frame()));
        shape.setFillColor(colorOf(theme, "surface", new Color(0xF4, 0xF4, 0xF4)));
        shape.setLineColor(colorOf(theme, "divider", new Color(0xCC, 0xCC, 0xCC)));
        shape.setLineWidth(1.0);
        shape.setLineDash(LineDash.DASH);
        shape.setVerticalAlignment(VerticalAlignment.MIDDLE);
        shape.setInsets(new Insets(0, 0, 0, 0));

        String label = box.altText() == null || box.altText().isBlank() ? "Image" : box.altText();
        XSLFTextParagraph paragraph = shape.addNewTextParagraph();
        paragraph.setTextAlign(TextAlign.CENTER);
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(label);
        run.setFontFamily("SansSerif");
        run.setFontSize(9.0);
        run.setFontColor(colorOf(theme, "muted", new Color(0x88, 0x88, 0x88)));
    }

    private static PictureType pictureTypeOf(Asset asset) {
        String ref = asset.storedRef() == null ? "" : asset.storedRef().toLowerCase();
        if (ref.endsWith(".jpg") || ref.endsWith(".jpeg")) {
            return PictureType.JPEG;
        }
        if (ref.endsWith(".gif")) {
            return PictureType.GIF;
        }
        if (ref.endsWith(".bmp")) {
            return PictureType.BMP;
        }
        return PictureType.PNG;
    }

    private static Rectangle2D rectangle(Frame frame) {
        return new Rectangle2D.Double(frame.x(), frame.y(), frame.w(), frame.h());
    }

    private static Color colorOf(Theme theme, String role, Color fallback) {
        if (role == null) {
            return fallback;
        }
        String hex = theme.colors().get(role);
        return hex == null ? fallback : Color.decode(hex);
    }
}
