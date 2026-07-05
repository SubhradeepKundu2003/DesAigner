package com.tcs.contentGenerator.web;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.persistence.DesignNotFoundException;
import com.tcs.contentGenerator.persistence.DesignStore;
import com.tcs.contentGenerator.persistence.FlagStore;
import com.tcs.contentGenerator.persistence.ValidationFlagRecord;
import com.tcs.contentGenerator.render.ExportFormat;
import com.tcs.contentGenerator.render.RendererRegistry;

/**
 * The editor's REST contract (see TASKS.md §3.10): load and save the design
 * model (optimistic locking via its {@code revision} — a stale save gets 409),
 * export it to any registered format, and review/resolve fact-validation
 * flags. Export always renders the <em>saved</em>, possibly human-edited
 * design, and is gated: while any unresolved flag at blocking severity exists,
 * export answers 409 until the flags are resolved or waived here.
 */
@RestController
@RequestMapping("/api/designs")
public class DesignApiController {

    private final DesignStore designStore;
    private final FlagStore flagStore;
    private final RendererRegistry rendererRegistry;

    public DesignApiController(DesignStore designStore, FlagStore flagStore,
            RendererRegistry rendererRegistry) {
        this.designStore = designStore;
        this.flagStore = flagStore;
        this.rendererRegistry = rendererRegistry;
    }

    @GetMapping("/{jobId}")
    public DesignDocument load(@PathVariable String jobId) {
        return designStore.load(jobId).orElseThrow(() -> new DesignNotFoundException(jobId));
    }

    /**
     * Saves an edited design. The body's {@code revision} must be the revision
     * the editor loaded; the response body is the stored document with the
     * revision bumped, so the editor can keep saving without reloading.
     */
    @PutMapping("/{jobId}")
    public DesignDocument save(@PathVariable String jobId, @RequestBody DesignDocument document) {
        return designStore.saveEdit(jobId, document);
    }

    /**
     * Renders the saved design to the requested format. GET as well as POST so
     * a plain download link works (the render has no side effects).
     */
    @RequestMapping(value = "/{jobId}/export", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<byte[]> export(@PathVariable String jobId, @RequestParam String format) {
        ExportFormat exportFormat = ExportFormat.fromName(format)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown export format '" + format + "'"));
        DesignDocument document = designStore.load(jobId)
                .orElseThrow(() -> new DesignNotFoundException(jobId));
        if (flagStore.exportBlocked(jobId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Export blocked: unresolved fact-validation flags at blocking severity — "
                            + "review them at /api/designs/" + jobId + "/flags");
        }
        byte[] bytes = rendererRegistry.render(exportFormat, document);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportFormat.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"newsletter-"
                        + jobId + "." + exportFormat.fileExtension() + "\"")
                .body(bytes);
    }

    @GetMapping("/{jobId}/flags")
    public FlagsResponse flags(@PathVariable String jobId) {
        if (!designStore.exists(jobId)) {
            throw new DesignNotFoundException(jobId);
        }
        List<FlagView> flags = flagStore.findByJob(jobId).stream().map(FlagView::from).toList();
        return new FlagsResponse(flagStore.exportBlocked(jobId), flags);
    }

    /** Resolves (or waives) one flag; body is optional: {@code {"note": "..."}}. */
    @PostMapping("/{jobId}/flags/{flagId}/resolve")
    public FlagView resolve(@PathVariable String jobId, @PathVariable long flagId,
            @RequestBody(required = false) ResolveRequest request) {
        return FlagView.from(flagStore.resolve(jobId, flagId, request == null ? null : request.note()));
    }

    public record ResolveRequest(String note) {
    }

    public record FlagsResponse(boolean exportBlocked, List<FlagView> flags) {
    }

    public record FlagView(long id, String sectionTitle, String articleHeadline, String claim,
            String severity, String issue, boolean resolved, String resolutionNote) {

        static FlagView from(ValidationFlagRecord record) {
            return new FlagView(record.getId(), record.getSectionTitle(), record.getArticleHeadline(),
                    record.getClaim(), record.getSeverity().label(), record.getIssue(),
                    record.isResolved(), record.getResolutionNote());
        }
    }
}
