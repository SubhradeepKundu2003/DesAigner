package com.tcs.contentGenerator.samples;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

/**
 * Not a test — a manual generator (IT suffix keeps it out of the default
 * suite) that writes {@code samples/TD_Sample_WithImages.docx}: a realistic
 * multi-section business write-up with three embedded photos, so the
 * source-document-image path of Agent #8 can be exercised end-to-end.
 * Regenerate with {@code ./mvnw test -Dtest=SampleDocGeneratorIT}.
 */
class SampleDocGeneratorIT {

    @Test
    void generateSampleDocxWithImages() throws Exception {
        Path out = Path.of("samples/TD_Sample_WithImages.docx");
        Files.createDirectories(out.getParent());

        try (XWPFDocument doc = new XWPFDocument()) {
            heading(doc, "TD Monthly Source Pack — August 2026");

            heading(doc, "Delivery Highlights");
            text(doc, "The Apollo programme closed its second release two weeks ahead of plan in "
                    + "August 2026. Client satisfaction climbed again, with the Net Promoter Score "
                    + "reaching NPS: 74 across all engaged accounts.");
            text(doc, "The release added automated regression coverage for the payments module and "
                    + "cut the average deployment window from four hours to forty minutes.");
            picture(doc, photo(new Color(0x0B5563), new Color(0x3AA6B9), "Apollo release team"));

            heading(doc, "Customer Success");
            text(doc, "A rural literacy initiative delivered with our long-standing public sector "
                    + "client reached its 5,000th learner this month. Field teams reported that "
                    + "evening classes now run in 42 villages, with attendance holding above 80 "
                    + "percent through the harvest season.");
            picture(doc, photo(new Color(0x8A4B08), new Color(0xF4A259), "Literacy programme in the field"));

            heading(doc, "Project Updates");
            text(doc, "The data platform migration entered its final phase. All nine reporting "
                    + "pipelines now run on the new stack, and the legacy warehouse is scheduled "
                    + "for decommissioning in September.");
            text(doc, "The mobile workforce app pilot expanded to two additional regions after "
                    + "positive feedback from the first 300 field engineers.");
            picture(doc, photo(new Color(0x2B2E6B), new Color(0x6E7BD9), "Data platform migration"));

            heading(doc, "Training and Learning");
            text(doc, "The cloud certification drive concluded with 120 engineers earning associate "
                    + "credentials this quarter. A new applied AI curriculum opens for enrolment "
                    + "next month.");

            heading(doc, "Upcoming Events");
            text(doc, "Quarterly town hall on 12 September. Innovation day submissions close 20 "
                    + "September. The annual client summit is confirmed for the first week of "
                    + "October in Mumbai.");

            try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
                doc.write(fos);
            }
        }
        System.out.println("Wrote " + out.toAbsolutePath() + " (" + Files.size(out) + " bytes)");
    }

    private static void heading(XWPFDocument doc, String textContent) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(16);
        r.setText(textContent);
    }

    private static void text(XWPFDocument doc, String textContent) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(textContent);
    }

    private static void picture(XWPFDocument doc, byte[] png) throws Exception {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().addPicture(new ByteArrayInputStream(png), XWPFDocument.PICTURE_TYPE_PNG,
                "photo.png", Units.toEMU(420), Units.toEMU(280));
    }

    /** Abstract gradient "photo" with soft shapes and a label — a stand-in for a real photograph. */
    private static byte[] photo(Color from, Color to, String label) throws Exception {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, from, 900, 600, to));
        g.fillRect(0, 0, 900, 600);
        g.setColor(new Color(255, 255, 255, 34));
        g.fillOval(520, -180, 560, 560);
        g.fillOval(-160, 300, 480, 480);
        g.setColor(new Color(255, 255, 255, 60));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.drawString(label, 48, 552);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
