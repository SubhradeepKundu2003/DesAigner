package com.tcs.contentGenerator.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tcs.contentGenerator.document.DocumentType;
import com.tcs.contentGenerator.orchestrator.AgentOrchestrator;
import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Entry point for the pipeline: accepts document uploads, stores them, then runs
 * the agent chain (currently just the ingestion agent) and returns a summary.
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final StorageService storage;
    private final AgentOrchestrator orchestrator;

    public IngestionController(StorageService storage, AgentOrchestrator orchestrator) {
        this.storage = storage;
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public IngestionResponse ingest(@RequestParam("files") MultipartFile[] files) {
        String jobId = UUID.randomUUID().toString();
        List<StoredFile> stored = new ArrayList<>();
        for (MultipartFile file : files) {
            String original = file.getOriginalFilename();
            DocumentType type = DocumentType.fromFilename(original);
            String ref = storage.store("jobs/" + jobId + "/inputs/" + original, bytesOf(file));
            stored.add(new StoredFile(original, type, ref, file.getSize()));
        }
        PipelineContext context = new PipelineContext(jobId, stored);
        orchestrator.run(context);
        return IngestionResponse.from(context);
    }

    private byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload " + file.getOriginalFilename(), e);
        }
    }
}
