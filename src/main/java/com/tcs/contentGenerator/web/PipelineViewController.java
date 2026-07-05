package com.tcs.contentGenerator.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.orchestrator.PipelineService;
import com.tcs.contentGenerator.render.ExportFormat;
import com.tcs.contentGenerator.render.RendererRegistry;
import com.tcs.contentGenerator.render.html.HtmlDesignRenderer;

/**
 * Server-rendered web UI for the pipeline: upload documents, run the agent chain,
 * and review every agent's output on one page (ingested documents, classified
 * content items, and the planned newsletter issue). Backed by the same
 * {@link PipelineService} path as the JSON API. Templates live under
 * {@code templates/} with shared fragments in {@code templates/fragments/page.html}
 * and styling in {@code static/css/app.css}.
 */
@Controller
public class PipelineViewController {

    private final PipelineService pipeline;
    private final HtmlDesignRenderer htmlRenderer;
    private final RendererRegistry rendererRegistry;

    /**
     * Dev-harness cache of the last design produced per job, so the export link
     * below can render on demand without re-running the pipeline. Real
     * persistence (Postgres jsonb + revision) lands with Phase C's editor API.
     */
    private final Map<String, DesignDocument> designByJobId = new ConcurrentHashMap<>();

    public PipelineViewController(PipelineService pipeline, HtmlDesignRenderer htmlRenderer,
            RendererRegistry rendererRegistry) {
        this.pipeline = pipeline;
        this.htmlRenderer = htmlRenderer;
        this.rendererRegistry = rendererRegistry;
    }

    @GetMapping("/")
    public String uploadForm() {
        return "index";
    }

    @PostMapping("/run")
    public String run(@RequestParam("files") MultipartFile[] files, Model model) {
        PipelineContext context = pipeline.run(files);
        model.addAttribute("result", IngestionResponse.from(context));
        if (context.getDesignDocument() != null) {
            String html = new String(htmlRenderer.render(context.getDesignDocument()), StandardCharsets.UTF_8);
            model.addAttribute("designHtml", html);
            designByJobId.put(context.getJobId(), context.getDesignDocument());
        }
        return "result";
    }

    @GetMapping("/design/{jobId}/export.{extension}")
    @ResponseBody
    public ResponseEntity<byte[]> export(@PathVariable String jobId, @PathVariable String extension) {
        DesignDocument design = designByJobId.get(jobId);
        if (design == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No design found for job " + jobId);
        }
        ExportFormat format;
        try {
            format = ExportFormat.valueOf(extension.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No export format ." + extension);
        }
        String mediaType = switch (format) {
            case PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case PDF -> "application/pdf";
            case HTML -> "text/html";
        };
        byte[] bytes = rendererRegistry.render(format, design);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"newsletter-" + jobId + "." + extension.toLowerCase() + "\"")
                .body(bytes);
    }
}
