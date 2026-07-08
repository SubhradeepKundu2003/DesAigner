package com.tcs.contentGenerator.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tcs.contentGenerator.styleextraction.StyleExtractionService;
import com.tcs.contentGenerator.styleextraction.StyleExtractionService.StyleExtractionResult;

/**
 * Triggers reference style extraction over the PDFs staged under
 * {@code storage/references/}. POST-only: the run costs several vision LLM
 * calls (minutes on this CPU-only machine) and writes a draft template file.
 */
@RestController
@RequestMapping("/api/style-extraction")
public class StyleExtractionController {

    private final StyleExtractionService styleExtractionService;

    public StyleExtractionController(StyleExtractionService styleExtractionService) {
        this.styleExtractionService = styleExtractionService;
    }

    @PostMapping("/run")
    public StyleExtractionResult run() {
        try {
            return styleExtractionService.extract();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }
}
