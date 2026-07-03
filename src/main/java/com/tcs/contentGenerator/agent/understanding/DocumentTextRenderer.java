package com.tcs.contentGenerator.agent.understanding;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.document.DocumentBlock;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.HeadingBlock;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.TableBlock;
import com.tcs.contentGenerator.document.TextBlock;

/**
 * Flattens a normalized {@link DocumentModel} into a compact plain-text rendering
 * suitable for feeding to the LLM. Headings become markdown-ish titles, tables are
 * rendered as pipe-delimited rows, and images are represented by their caption so
 * the model still sees them referenced without the bytes.
 */
@Component
public class DocumentTextRenderer {

    /** Cap rendered table rows so a huge sheet can't blow the model's context. */
    private static final int MAX_TABLE_ROWS = 50;

    public String render(DocumentModel document) {
        StringBuilder sb = new StringBuilder();
        for (DocumentBlock block : document.blocks()) {
            switch (block) {
                case HeadingBlock h -> sb.append("#".repeat(Math.max(1, h.level())))
                        .append(' ').append(h.text()).append("\n\n");
                case TextBlock t -> sb.append(t.text()).append("\n\n");
                case TableBlock t -> appendTable(sb, t);
                case ImageBlock i -> {
                    if (i.caption() != null && !i.caption().isBlank()) {
                        sb.append("[Image: ").append(i.caption()).append("]\n\n");
                    }
                }
            }
        }
        return sb.toString().strip();
    }

    private void appendTable(StringBuilder sb, TableBlock table) {
        if (!table.headers().isEmpty()) {
            sb.append(String.join(" | ", table.headers())).append('\n');
        }
        List<List<String>> rows = table.rows();
        int limit = Math.min(rows.size(), MAX_TABLE_ROWS);
        for (int i = 0; i < limit; i++) {
            sb.append(String.join(" | ", rows.get(i))).append('\n');
        }
        if (rows.size() > limit) {
            sb.append("... (").append(rows.size() - limit).append(" more rows)\n");
        }
        sb.append('\n');
    }
}
