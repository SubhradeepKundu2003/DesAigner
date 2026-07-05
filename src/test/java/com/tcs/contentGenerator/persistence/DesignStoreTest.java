package com.tcs.contentGenerator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;

import tools.jackson.databind.json.JsonMapper;

/**
 * The editor-save contract: a save must present the revision it loaded, the
 * stored JSON must carry the bumped revision (the stored document and the wire
 * document are the same schema), and a guarded update that matches no row must
 * surface as "stale" or "gone" — never as a silent success.
 */
class DesignStoreTest {

    private static final String JOB = "job-123";

    private final JsonMapper mapper = new JsonMapper();
    private DesignRecordRepository repository;
    private DesignStore store;

    @BeforeEach
    void setUp() {
        repository = mock(DesignRecordRepository.class);
        store = new DesignStore(repository, mapper);
    }

    @Test
    void saveEditBumpsRevisionInReturnedAndStoredJson() {
        when(repository.updateIfRevisionMatches(eq(JOB), anyString(), eq(3L), any(Instant.class)))
                .thenReturn(1);

        DesignDocument saved = store.saveEdit(JOB, fixture(3));

        assertEquals(4, saved.revision());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(repository)
                .updateIfRevisionMatches(eq(JOB), json.capture(), eq(3L), any(Instant.class));
        DesignDocument stored = mapper.readValue(json.getValue(), DesignDocument.class);
        assertEquals(saved, stored);
    }

    @Test
    void staleSaveRaisesConflict() {
        when(repository.updateIfRevisionMatches(anyString(), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(0);
        when(repository.existsById(JOB)).thenReturn(true);

        assertThrows(StaleRevisionException.class, () -> store.saveEdit(JOB, fixture(3)));
    }

    @Test
    void saveForMissingDesignRaisesNotFound() {
        when(repository.updateIfRevisionMatches(anyString(), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(0);
        when(repository.existsById(JOB)).thenReturn(false);

        assertThrows(DesignNotFoundException.class, () -> store.saveEdit(JOB, fixture(3)));
    }

    @Test
    void loadRoundTripsWhatSaveNewStored() {
        DesignDocument original = fixture(1);
        ArgumentCaptor<DesignRecord> record = ArgumentCaptor.forClass(DesignRecord.class);
        when(repository.save(record.capture())).thenAnswer(inv -> inv.getArgument(0));

        store.saveNew(JOB, original);

        assertEquals(1, record.getValue().getRevision());
        assertTrue(record.getValue().getDocument().contains("\"revision\""));
        when(repository.findById(JOB)).thenReturn(Optional.of(record.getValue()));

        assertEquals(Optional.of(original), store.load(JOB));
    }

    private DesignDocument fixture(long revision) {
        Theme theme = new Theme(
                new PageSize(595.28, 841.89),
                Map.of("background", "#FFFFFF", "primary", "#0B5FFF"),
                Map.of("Body", new TextStyle("SansSerif", 10, "normal", "text", 14)),
                new Spacing(48, 16));
        TextBox text = new TextBox("cmp-1", ComponentRole.ARTICLE_HEADLINE,
                new Frame(48, 48, 300, 20), 0, false, null, "Headline", "NPS climbs to 72");
        return new DesignDocument(1, revision, new DesignMeta("TD Monthly — July 2026", JOB),
                theme, List.of(), List.of(new Page("page-1", List.of(text))));
    }
}
