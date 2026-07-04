package com.tcs.contentGenerator.agent.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Agent #5 of the pipeline. Fact-checks every generated article against the
 * source material it was written from, via the provenance chain
 * ({@link GeneratedArticle#source()} → {@link ContentItem#sources()} →
 * {@link SourceTextResolver}). Two independent checks run per article:
 *
 * <ul>
 *   <li>a deterministic numeric cross-check — every figure in the article must
 *       appear in the source material (catches hallucinated numbers for free,
 *       no LLM call); and</li>
 *   <li>one LLM call comparing the article to the source, returning a bare
 *       {@link ClaimFlag} array of contradicted / unsupported claims.</li>
 * </ul>
 *
 * <p>The Leadership Message has no source items by design and is skipped. A
 * failed check degrades to a LOW flag on that one article, never the run. The
 * export gate is deterministic Java: {@link ValidationReport#exportBlocked()}
 * is true while any flag at or above {@code app.validation.blocking-severity}
 * exists.
 */
@Component
@Order(5)
public class FactValidationAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(FactValidationAgent.class);
    /** A number as written in prose: digits with optional thousands commas / decimals. */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private final LlmClient llm;
    private final SourceTextResolver resolver;
    private final ValidationSeverity blockingSeverity;

    public FactValidationAgent(LlmClient llm, SourceTextResolver resolver,
            @Value("${app.validation.blocking-severity:high}") String blockingSeverity) {
        this.llm = llm;
        this.resolver = resolver;
        this.blockingSeverity = ValidationSeverity.fromLabel(blockingSeverity);
    }

    @Override
    public String name() {
        return "Fact Validation Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        GeneratedNewsletter newsletter = context.getGeneratedNewsletter();
        if (newsletter == null || newsletter.sections().isEmpty()) {
            log.info("No generated newsletter to validate, skipping fact validation");
            context.setValidationReport(new ValidationReport(List.of(), 0, 0, false));
            return;
        }

        List<ValidationFlag> flags = new ArrayList<>();
        int checked = 0;
        int skipped = 0;
        for (GeneratedSection section : newsletter.sections()) {
            for (GeneratedArticle article : section.articles()) {
                if (article.source() == null) {
                    // The Leadership Message is written from an issue digest,
                    // not source documents — there is nothing to check it against.
                    skipped++;
                    continue;
                }
                checked++;
                flags.addAll(validate(section.section().title(), article,
                        newsletter.issueTitle(), context.getDocuments()));
            }
        }

        boolean blocked = flags.stream().anyMatch(f -> f.severity().meetsOrExceeds(blockingSeverity));
        ValidationReport report = new ValidationReport(flags, checked, skipped, blocked);
        context.setValidationReport(report);
        log.info("Fact validation checked {} article(s) ({} skipped): {} flag(s), export {}",
                checked, skipped, flags.size(), blocked ? "BLOCKED" : "clear");
    }

    private List<ValidationFlag> validate(String sectionTitle, GeneratedArticle article,
            String issueTitle, List<DocumentModel> documents) {
        ContentItem item = article.source().item();
        String sourceText = resolver.resolve(item, documents);
        if (sourceText.isBlank()) {
            log.warn("[{}] no source material resolved for \"{}\", cannot fact-check",
                    sectionTitle, article.headline());
            return List.of(new ValidationFlag(sectionTitle, article.headline(), "",
                    ValidationSeverity.LOW,
                    "Source material could not be located; this article was not fact-checked."));
        }

        // Numbers may legitimately come from the extracted item or the issue title
        // (e.g. the month/year), not just the raw source text — check against all.
        String numberCorpus = String.join("\n", sourceText, item.title(), item.summary(),
                String.join("\n", item.keyMetrics()), issueTitle);
        List<ValidationFlag> flags = new ArrayList<>(
                checkNumbers(sectionTitle, article, numberCorpus));

        try {
            ClaimFlag[] found = llm.generate(
                    ValidationPrompts.SYSTEM,
                    ValidationPrompts.USER_TEMPLATE.formatted(
                            sourceText, article.headline(), article.body()),
                    ClaimFlag[].class);
            flags.addAll(toFlags(found, sectionTitle, article.headline()));
            log.info("[{}] fact-checked \"{}\": {} flag(s)",
                    sectionTitle, article.headline(), flags.size());
        } catch (Exception e) {
            log.warn("[{}] fact-check call failed for \"{}\": {}",
                    sectionTitle, article.headline(), e.toString());
            flags.add(new ValidationFlag(sectionTitle, article.headline(), "",
                    ValidationSeverity.LOW,
                    "Automated fact-check could not run for this article."));
        }
        return flags;
    }

    /**
     * Deterministic check: every figure written into the article must appear in
     * the source corpus. Tokens are compared comma-stripped ("15,000" matches
     * "15000"); a substring pass over the corpus digits catches reformatting.
     * Misses are MEDIUM, not HIGH — a figure the source states differently
     * (e.g. "9.5 million" vs "9,500,000") is for the LLM check / a human to judge.
     */
    private List<ValidationFlag> checkNumbers(String sectionTitle, GeneratedArticle article,
            String corpus) {
        Set<String> corpusNumbers = numberTokens(corpus);
        String corpusDigits = corpus.replace(",", "");
        List<ValidationFlag> flags = new ArrayList<>();
        for (String token : numberTokens(article.headline() + "\n" + article.body())) {
            if (corpusNumbers.contains(token) || corpusDigits.contains(token)) {
                continue;
            }
            flags.add(new ValidationFlag(sectionTitle, article.headline(), token,
                    ValidationSeverity.MEDIUM,
                    "This figure does not appear in the source material."));
        }
        return flags;
    }

    private static Set<String> numberTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            tokens.add(m.group().replace(",", ""));
        }
        return tokens;
    }

    /** Maps the model's findings onto flags, null-guarding every field. */
    private static List<ValidationFlag> toFlags(ClaimFlag[] found, String sectionTitle,
            String headline) {
        if (found == null) {
            return List.of();
        }
        List<ValidationFlag> flags = new ArrayList<>();
        for (ClaimFlag raw : found) {
            if (raw == null) {
                continue;
            }
            String claim = raw.claim() == null ? "" : raw.claim().strip();
            String issue = raw.issue() == null ? "" : raw.issue().strip();
            if (claim.isBlank() && issue.isBlank()) {
                continue;
            }
            flags.add(new ValidationFlag(sectionTitle, headline, claim,
                    ValidationSeverity.fromLabel(raw.severity()), issue));
        }
        return flags;
    }
}
