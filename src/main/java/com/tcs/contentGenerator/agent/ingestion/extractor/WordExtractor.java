package com.tcs.contentGenerator.agent.ingestion.extractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.ingestion.DocumentExtractor;
import com.tcs.contentGenerator.agent.ingestion.ExtractionContext;
import com.tcs.contentGenerator.agent.ingestion.IngestionException;
import com.tcs.contentGenerator.document.DocumentBlock;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.DocumentType;
import com.tcs.contentGenerator.document.HeadingBlock;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.SourceRef;
import com.tcs.contentGenerator.document.TableBlock;
import com.tcs.contentGenerator.document.TextBlock;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.storage.StoredFile;

/** Extracts paragraphs, headings, tables and images from {@code .docx} files. */
@Component
public class WordExtractor extends AbstractDocumentExtractor implements DocumentExtractor {

    public WordExtractor(StorageService storage) {
        super(storage);
    }

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.DOCX;
    }

    @Override
    public DocumentModel extract(StoredFile file, ExtractionContext ctx) {
        List<DocumentBlock> blocks = new ArrayList<>();
        int seq = 0;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(read(file)))) {
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    SourceRef ref = new SourceRef(file.originalFilename(), "Body", seq++);
                    String style = paragraph.getStyle();
                    if (style != null && style.toLowerCase(Locale.ROOT).contains("heading")) {
                        blocks.add(new HeadingBlock(headingLevel(style), text.trim(), ref));
                    } else {
                        blocks.add(new TextBlock(text.trim(), ref));
                    }
                } else if (element instanceof XWPFTable table) {
                    List<List<String>> rows = readTable(table);
                    if (!rows.isEmpty()) {
                        SourceRef ref = new SourceRef(file.originalFilename(), "Body", seq++);
                        blocks.add(new TableBlock(rows.get(0), new ArrayList<>(rows.subList(1, rows.size())), ref));
                    }
                }
            }
            for (XWPFPictureData pic : doc.getAllPictures()) {
                String ext = pic.suggestFileExtension();
                String storedRef = storeImage(ctx, ext, pic.getData());
                SourceRef ref = new SourceRef(file.originalFilename(), "Body", seq++);
                blocks.add(new ImageBlock(storedRef, null, "image/" + ext, ref));
            }
        } catch (IOException e) {
            throw new IngestionException("Failed to read docx: " + file.originalFilename(), e);
        }
        return new DocumentModel(metadata(file), blocks);
    }

    private List<List<String>> readTable(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText());
            }
            rows.add(cells);
        }
        return rows;
    }

    /** Pull the trailing digit out of a style id like "Heading2" or "Heading 2". */
    private int headingLevel(String style) {
        for (int i = style.length() - 1; i >= 0; i--) {
            if (Character.isDigit(style.charAt(i))) {
                return Character.getNumericValue(style.charAt(i));
            }
        }
        return 1;
    }
}
