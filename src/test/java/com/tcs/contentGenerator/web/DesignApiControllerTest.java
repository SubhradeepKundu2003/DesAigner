package com.tcs.contentGenerator.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.persistence.DesignStore;
import com.tcs.contentGenerator.persistence.FlagStore;
import com.tcs.contentGenerator.render.DesignRenderer;
import com.tcs.contentGenerator.render.ExportFormat;
import com.tcs.contentGenerator.render.RendererRegistry;

/**
 * A renderer failure used to propagate as a bare, undiagnosable 500 (no log
 * line pointing at the job/format, generic Spring Whitelabel body) — this is
 * exactly what turns a real, content-triggered bug (see {@code
 * HtmlDesignRendererTest}/{@code PdfDesignRendererTest}'s quote-in-altText
 * fix) into a mysterious "sometimes the download doesn't work" report with no
 * way to diagnose which job or format failed. {@link DesignApiController
 * #export} now catches renderer failures and answers a 500 whose message
 * names the job and format.
 */
class DesignApiControllerTest {

    private static final DesignDocument DOCUMENT = new DesignDocument(1, 1,
            new DesignMeta("Issue", "job-1"),
            new Theme(new PageSize(595, 842), java.util.Map.of(), java.util.Map.of(), new Spacing(48, 16)),
            List.of(), List.of());

    @Test
    void aRendererFailureBecomesADiagnosable500NotABareStackTrace() {
        DesignStore designStore = mock(DesignStore.class);
        when(designStore.load("job-1")).thenReturn(Optional.of(DOCUMENT));
        FlagStore flagStore = mock(FlagStore.class);
        when(flagStore.exportBlocked(anyString())).thenReturn(false);
        DesignRenderer brokenPdfRenderer = new DesignRenderer() {
            @Override
            public ExportFormat format() {
                return ExportFormat.PDF;
            }

            @Override
            public byte[] render(DesignDocument document) {
                throw new IllegalStateException("simulated renderer failure");
            }
        };
        DesignApiController controller = new DesignApiController(designStore, flagStore,
                new RendererRegistry(List.of(brokenPdfRenderer)));

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> controller.export("job-1", "pdf"));

        assertTrue(thrown.getStatusCode().is5xxServerError(), "expected a 500, got " + thrown.getStatusCode());
        assertTrue(thrown.getReason() != null && thrown.getReason().contains("job-1")
                        && thrown.getReason().contains("pdf"),
                "expected the error message to name the job and format for diagnosis: " + thrown.getReason());
    }
}
