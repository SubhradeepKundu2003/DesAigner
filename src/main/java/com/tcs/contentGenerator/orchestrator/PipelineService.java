package com.tcs.contentGenerator.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tcs.contentGenerator.document.DocumentType;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Application service that turns uploaded files into a completed pipeline run:
 * it stores each upload, seeds a {@link PipelineContext}, and drives the whole
 * agent chain via the {@link AgentOrchestrator}. Shared by the REST endpoint and
 * the (temporary) Thymeleaf UI so both exercise the exact same path.
 */
@Service
public class PipelineService {

    private final StorageService storage;
    private final AgentOrchestrator orchestrator;

    public PipelineService(StorageService storage, AgentOrchestrator orchestrator) {
        this.storage = storage;
        this.orchestrator = orchestrator;
    }

    /** Store the uploads, run every agent in order, and return the final context. */
    public PipelineContext run(MultipartFile[] files) {
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
        return context;
    }

    private byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload " + file.getOriginalFilename(), e);
        }
    }
}
