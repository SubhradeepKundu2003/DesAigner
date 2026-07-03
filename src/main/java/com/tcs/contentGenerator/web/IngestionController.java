package com.tcs.contentGenerator.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.orchestrator.PipelineService;

/**
 * JSON entry point for the pipeline: accepts document uploads, runs the agent
 * chain via {@link PipelineService}, and returns a summary of what each agent
 * produced.
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final PipelineService pipeline;

    public IngestionController(PipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping
    public IngestionResponse ingest(@RequestParam("files") MultipartFile[] files) {
        PipelineContext context = pipeline.run(files);
        return IngestionResponse.from(context);
    }
}
