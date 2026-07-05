package com.tcs.contentGenerator.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tcs.contentGenerator.document.DocumentType;
import com.tcs.contentGenerator.persistence.DesignStore;
import com.tcs.contentGenerator.persistence.FlagStore;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.storage.StoredFile;

/**
 * Application service that turns uploaded files into a completed pipeline run:
 * it stores each upload, seeds a {@link PipelineContext}, drives the whole
 * agent chain via the {@link AgentOrchestrator}, and persists the run's
 * lasting outputs — the design document (revision 1, the editor API's working
 * copy) and the fact-validation flags (which the export gate reads live).
 * Shared by the REST endpoint and the dev Thymeleaf UI so both exercise the
 * exact same path.
 */
@Service
public class PipelineService {

    private final StorageService storage;
    private final AgentOrchestrator orchestrator;
    private final DesignStore designStore;
    private final FlagStore flagStore;

    public PipelineService(StorageService storage, AgentOrchestrator orchestrator,
            DesignStore designStore, FlagStore flagStore) {
        this.storage = storage;
        this.orchestrator = orchestrator;
        this.designStore = designStore;
        this.flagStore = flagStore;
    }

    /** Store the uploads, run every agent in order, persist the outputs, return the final context. */
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
        persistOutputs(context);
        return context;
    }

    /**
     * Deliberately outside any pipeline-wide transaction (the run takes minutes);
     * each store commits its own short transaction after the agents finish.
     */
    private void persistOutputs(PipelineContext context) {
        if (context.getValidationReport() != null) {
            flagStore.saveNew(context.getJobId(), context.getValidationReport().flags());
        }
        if (context.getDesignDocument() != null) {
            designStore.saveNew(context.getJobId(), context.getDesignDocument());
        }
    }

    private byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload " + file.getOriginalFilename(), e);
        }
    }
}
