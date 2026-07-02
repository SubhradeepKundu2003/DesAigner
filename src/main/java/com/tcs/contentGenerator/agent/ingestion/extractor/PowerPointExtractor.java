package com.tcs.contentGenerator.agent.ingestion.extractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.ingestion.DocumentExtractor;
import com.tcs.contentGenerator.agent.ingestion.ExtractionContext;
import com.tcs.contentGenerator.agent.ingestion.IngestionException;
import com.tcs.contentGenerator.document.DocumentBlock;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.DocumentType;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.SourceRef;
import com.tcs.contentGenerator.document.TableBlock;
import com.tcs.contentGenerator.document.TextBlock;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.storage.StoredFile;

/** Extracts per-slide text, tables and pictures from {@code .pptx} decks. */
@Component
public class PowerPointExtractor extends AbstractDocumentExtractor implements DocumentExtractor {

    public PowerPointExtractor(StorageService storage) {
        super(storage);
    }

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.PPTX;
    }

    @Override
    public DocumentModel extract(StoredFile file, ExtractionContext ctx) {
        List<DocumentBlock> blocks = new ArrayList<>();
        int seq = 0;
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(read(file)))) {
            int slideNumber = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                slideNumber++;
                String location = "Slide:" + slideNumber;
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTable table) {
                        List<List<String>> rows = readTable(table);
                        if (!rows.isEmpty()) {
                            SourceRef ref = new SourceRef(file.originalFilename(), location, seq++);
                            blocks.add(new TableBlock(rows.get(0),
                                    new ArrayList<>(rows.subList(1, rows.size())), ref));
                        }
                    } else if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            blocks.add(new TextBlock(text.trim(),
                                    new SourceRef(file.originalFilename(), location, seq++)));
                        }
                    } else if (shape instanceof XSLFPictureShape pictureShape) {
                        XSLFPictureData data = pictureShape.getPictureData();
                        String storedRef = storeImage(ctx, data.suggestFileExtension(), data.getData());
                        blocks.add(new ImageBlock(storedRef, null, data.getContentType(),
                                new SourceRef(file.originalFilename(), location, seq++)));
                    }
                }
            }
        } catch (IOException e) {
            throw new IngestionException("Failed to read pptx: " + file.originalFilename(), e);
        }
        return new DocumentModel(metadata(file), blocks);
    }

    private List<List<String>> readTable(XSLFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XSLFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XSLFTableCell cell : row.getCells()) {
                cells.add(cell.getText());
            }
            rows.add(cells);
        }
        return rows;
    }
}
