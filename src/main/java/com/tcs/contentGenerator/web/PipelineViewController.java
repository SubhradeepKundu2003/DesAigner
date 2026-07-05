package com.tcs.contentGenerator.web;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.orchestrator.PipelineService;
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

    public PipelineViewController(PipelineService pipeline, HtmlDesignRenderer htmlRenderer) {
        this.pipeline = pipeline;
        this.htmlRenderer = htmlRenderer;
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
        }
        return "result";
    }
}
