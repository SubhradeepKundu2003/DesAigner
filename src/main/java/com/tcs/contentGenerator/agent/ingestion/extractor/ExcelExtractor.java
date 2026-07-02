package com.tcs.contentGenerator.agent.ingestion.extractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.storage.StoredFile;

/** Extracts sheets (as tables) and embedded images from {@code .xlsx} workbooks. */
@Component
public class ExcelExtractor extends AbstractDocumentExtractor implements DocumentExtractor {

    public ExcelExtractor(StorageService storage) {
        super(storage);
    }

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.XLSX;
    }

    @Override
    public DocumentModel extract(StoredFile file, ExtractionContext ctx) {
        List<DocumentBlock> blocks = new ArrayList<>();
        int seq = 0;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(read(file)))) {
            DataFormatter fmt = new DataFormatter();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                XSSFSheet sheet = wb.getSheetAt(s);
                List<List<String>> rows = readRows(sheet, fmt);
                if (rows.isEmpty()) {
                    continue;
                }
                SourceRef ref = new SourceRef(file.originalFilename(), "Sheet:" + sheet.getSheetName(), seq++);
                List<String> headers = rows.get(0);
                List<List<String>> body = new ArrayList<>(rows.subList(1, rows.size()));
                blocks.add(new TableBlock(headers, body, ref));
            }
            for (XSSFPictureData pic : wb.getAllPictures()) {
                String storedRef = storeImage(ctx, pic.suggestFileExtension(), pic.getData());
                SourceRef ref = new SourceRef(file.originalFilename(), "Workbook", seq++);
                blocks.add(new ImageBlock(storedRef, null, pic.getMimeType(), ref));
            }
        } catch (IOException e) {
            throw new IngestionException("Failed to read xlsx: " + file.originalFilename(), e);
        }
        return new DocumentModel(metadata(file), blocks);
    }

    private List<List<String>> readRows(XSSFSheet sheet, DataFormatter fmt) {
        List<List<String>> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<String> cells = new ArrayList<>();
            short last = row.getLastCellNum();
            for (int c = 0; c < last; c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                cells.add(cell == null ? "" : fmt.formatCellValue(cell));
            }
            rows.add(cells);
        }
        return rows;
    }
}
