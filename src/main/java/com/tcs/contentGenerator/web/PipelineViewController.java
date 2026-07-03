package com.tcs.contentGenerator.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.orchestrator.PipelineService;

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

    public PipelineViewController(PipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @GetMapping("/")
    public String uploadForm() {
        return "index";
    }

    @PostMapping("/run")
    public String run(@RequestParam("files") MultipartFile[] files, Model model) {
        PipelineContext context = pipeline.run(files);
        model.addAttribute("result", IngestionResponse.from(context));
        return "result";
    }
}
