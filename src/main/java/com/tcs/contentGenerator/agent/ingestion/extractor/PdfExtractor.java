package com.tcs.contentGenerator.agent.ingestion.extractor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.ingestion.DocumentExtractor;
import com.tcs.contentGenerator.agent.ingestion.ExtractionContext;
import com.tcs.contentGenerator.agent.ingestion.IngestionException;
import com.tcs.contentGenerator.document.DocumentBlock;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.DocumentType;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.SourceRef;
import com.tcs.contentGenerator.document.TextBlock;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Extracts per-page text and embedded images from {@code .pdf} files. PDFs have
 * no native table structure, so tabular data surfaces as {@link TextBlock} text
 * for now (see TASKS.md §3.1 — table reconstruction is a later enhancement).
 */
@Component
public class PdfExtractor extends AbstractDocumentExtractor implements DocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractor.class);

    public PdfExtractor(StorageService storage) {
        super(storage);
    }

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.PDF;
    }

    @Override
    public DocumentModel extract(StoredFile file, ExtractionContext ctx) {
        List<DocumentBlock> blocks = new ArrayList<>();
        int seq = 0;
        try (PDDocument doc = Loader.loadPDF(read(file))) {
            int pages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc);
                if (text != null && !text.isBlank()) {
                    blocks.add(new TextBlock(text.trim(),
                            new SourceRef(file.originalFilename(), "Page:" + page, seq++)));
                }
            }
            seq = extractImages(doc, file, ctx, blocks, seq);
        } catch (IOException e) {
            throw new IngestionException("Failed to read pdf: " + file.originalFilename(), e);
        }
        return new DocumentModel(metadata(file), blocks);
    }

    private int extractImages(PDDocument doc, StoredFile file, ExtractionContext ctx,
                              List<DocumentBlock> blocks, int seq) {
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage pdPage = doc.getPage(i);
            PDResources resources = pdPage.getResources();
            if (resources == null) {
                continue;
            }
            for (COSName name : resources.getXObjectNames()) {
                try {
                    PDXObject xobject = resources.getXObject(name);
                    if (xobject instanceof PDImageXObject image) {
                        BufferedImage buffered = image.getImage();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(buffered, "png", baos);
                        String storedRef = storeImage(ctx, "png", baos.toByteArray());
                        blocks.add(new ImageBlock(storedRef, null, "image/png",
                                new SourceRef(file.originalFilename(), "Page:" + (i + 1), seq++)));
                    }
                } catch (IOException e) {
                    log.warn("Skipping unreadable image on page {} of {}: {}",
                            i + 1, file.originalFilename(), e.getMessage());
                }
            }
        }
        return seq;
    }
}
