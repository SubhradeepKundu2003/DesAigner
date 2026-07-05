package com.tcs.contentGenerator.agent.compliance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/**
 * Agent #6 of the pipeline. Checks every generated article against the brand
 * rulebook ({@link ComplianceRules}, bound from {@code app.compliance.rules})
 * and fixes what it safely can, cheapest strategy first:
 *
 * <ul>
 *   <li><b>Terminology</b> — banned term with an approved replacement:
 *       deterministic case-preserving substitution, no LLM;</li>
 *   <li><b>Casing</b> — brand/product name written with the wrong casing:
 *       deterministic correction to the canonical spelling, no LLM;</li>
 *   <li><b>Banned phrases</b> — wording with no drop-in replacement: one LLM
 *       rewrite call for the whole article, accepted only if the banned
 *       wording is actually gone and no figures were invented — otherwise the
 *       article keeps its (deterministically fixed) text and the violation is
 *       reported unfixed for a human editor.</li>
 * </ul>
 *
 * <p>The corrected articles <em>replace</em> the {@link GeneratedNewsletter}
 * on the context so downstream agents (layout, export) see compliant text;
 * the {@link ComplianceReport} records every breach found, fixed or not.
 * There is no export gate here — unresolved breaches are editorial, not
 * factual, so they surface in the report without blocking the run.
 */
@Component
@Order(6)
public class BrandComplianceAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(BrandComplianceAgent.class);
    /** Same figure shape as the fact-check: digits with optional commas/decimals. */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    /** Anything longer than this in a rewrite's first line is body prose, not a headline. */
    private static final int MAX_HEADLINE_CHARS = 120;

    private final LlmClient llm;
    private final List<TermRule> termRules;
    private final List<NameRule> nameRules;
    private final List<PhraseRule> phraseRules;

    /** One rule pattern, compiled once at startup instead of per article. */
    private record TermRule(Pattern pattern, String replacement) {
    }

    private record NameRule(Pattern pattern, String canonical) {
    }

    private record PhraseRule(Pattern pattern, String phrase) {
    }

    /** A breach found in one article, before it is stamped with the article identity. */
    private record Finding(ViolationType type, String found, String replacement, boolean fixed) {
    }

    /** An article after checking: its (possibly corrected) text plus what was found. */
    private record CheckedArticle(GeneratedArticle article, List<ComplianceViolation> violations,
            boolean changed) {
    }

    public BrandComplianceAgent(LlmClient llm, ComplianceRules rules) {
        this.llm = llm;
        this.termRules = rules.terminology().entrySet().stream()
                .map(e -> new TermRule(wordPattern(e.getKey()), e.getValue()))
                .toList();
        this.nameRules = rules.properNames().stream()
                .map(name -> new NameRule(wordPattern(name), name))
                .toList();
        this.phraseRules = rules.bannedPhrases().stream()
                .map(phrase -> new PhraseRule(wordPattern(phrase), phrase))
                .toList();
    }

    private static Pattern wordPattern(String literal) {
        return Pattern.compile("\\b" + Pattern.quote(literal) + "\\b", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public String name() {
        return "Brand Compliance Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        GeneratedNewsletter newsletter = context.getGeneratedNewsletter();
        if (newsletter == null || newsletter.sections().isEmpty()) {
            log.info("No generated newsletter to check, skipping brand compliance");
            context.setComplianceReport(new ComplianceReport(List.of(), 0, 0));
            return;
        }

        List<ComplianceViolation> violations = new ArrayList<>();
        List<GeneratedSection> fixedSections = new ArrayList<>();
        int checked = 0;
        int fixed = 0;
        for (GeneratedSection section : newsletter.sections()) {
            List<GeneratedArticle> articles = new ArrayList<>();
            for (GeneratedArticle article : section.articles()) {
                checked++;
                CheckedArticle result = check(section.section().title(), article);
                articles.add(result.article());
                violations.addAll(result.violations());
                if (result.changed()) {
                    fixed++;
                }
            }
            fixedSections.add(new GeneratedSection(section.section(), articles));
        }

        // Downstream agents (layout, export) must see the compliant text.
        context.setGeneratedNewsletter(
                new GeneratedNewsletter(newsletter.issueTitle(), fixedSections));
        ComplianceReport report = new ComplianceReport(violations, checked, fixed);
        context.setComplianceReport(report);
        log.info("Brand compliance checked {} article(s): {} violation(s), {} fixed, {} unresolved",
                checked, violations.size(), report.fixedCount(), report.unresolvedCount());
    }

    private CheckedArticle check(String sectionTitle, GeneratedArticle article) {
        List<Finding> findings = new ArrayList<>();
        String headline = applyTermRules(applyNameRules(article.headline(), findings), findings);
        String body = applyTermRules(applyNameRules(article.body(), findings), findings);

        List<String> banned = bannedIn(headline + "\n" + body);
        if (!banned.isEmpty()) {
            String[] rewritten = rewrite(sectionTitle, headline, body, banned);
            boolean accepted = rewritten != null;
            if (accepted) {
                headline = rewritten[0];
                body = rewritten[1];
            }
            for (String phrase : banned) {
                findings.add(new Finding(ViolationType.BANNED_PHRASE, phrase, "", accepted));
            }
        }

        boolean changed = !headline.equals(article.headline()) || !body.equals(article.body());
        GeneratedArticle result = changed
                ? new GeneratedArticle(headline, body, article.source())
                : article;
        // The same term can occur in both headline and body — report it once.
        List<ComplianceViolation> violations = findings.stream()
                .distinct()
                .map(f -> new ComplianceViolation(sectionTitle, result.headline(),
                        f.type(), f.found(), f.replacement(), f.fixed()))
                .toList();
        if (!violations.isEmpty()) {
            log.info("[{}] \"{}\": {} violation(s), text {}",
                    sectionTitle, result.headline(), violations.size(),
                    changed ? "corrected" : "unchanged");
        }
        return new CheckedArticle(result, violations, changed);
    }

    /** Substitutes every banned term with its approved replacement, preserving case. */
    private String applyTermRules(String text, List<Finding> findings) {
        String result = text;
        for (TermRule rule : termRules) {
            Matcher m = rule.pattern().matcher(result);
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            while (m.find()) {
                String replacement = matchCase(m.group(), rule.replacement());
                findings.add(new Finding(ViolationType.TERMINOLOGY, m.group(), replacement, true));
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                any = true;
            }
            if (any) {
                m.appendTail(sb);
                result = sb.toString();
            }
        }
        return result;
    }

    /** Corrects wrongly cased brand/product names to their canonical spelling. */
    private String applyNameRules(String text, List<Finding> findings) {
        String result = text;
        for (NameRule rule : nameRules) {
            Matcher m = rule.pattern().matcher(result);
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            while (m.find()) {
                if (m.group().equals(rule.canonical())) {
                    continue;
                }
                findings.add(new Finding(ViolationType.CASING, m.group(), rule.canonical(), true));
                m.appendReplacement(sb, Matcher.quoteReplacement(rule.canonical()));
                any = true;
            }
            if (any) {
                m.appendTail(sb);
                result = sb.toString();
            }
        }
        return result;
    }

    /**
     * Mirrors the replacement's casing to the match: an ALL-CAPS match yields
     * an ALL-CAPS replacement, a Capitalized match a Capitalized one (so
     * "Utilize" at a sentence start becomes "Use", not "use").
     */
    private static String matchCase(String found, String replacement) {
        if (replacement.isEmpty()) {
            return replacement;
        }
        boolean hasLetters = found.chars().anyMatch(Character::isLetter);
        if (hasLetters && found.length() > 1 && found.equals(found.toUpperCase())) {
            return replacement.toUpperCase();
        }
        if (Character.isUpperCase(found.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement;
    }

    /** The banned phrases (in their configured form) present in {@code text}. */
    private List<String> bannedIn(String text) {
        List<String> present = new ArrayList<>();
        for (PhraseRule rule : phraseRules) {
            if (rule.pattern().matcher(text).find()) {
                present.add(rule.phrase());
            }
        }
        return present;
    }

    /**
     * One LLM call to rewrite the article without the banned wording. Returns
     * {@code {headline, body}} only when the rewrite is safe to accept: the
     * banned wording is actually gone, the body survived parsing, and every
     * figure in the rewrite already existed in the original (fact validation
     * has already run — a rewrite must not invent numbers behind its back).
     * Returns {@code null} otherwise; the caller keeps the original text.
     */
    private String[] rewrite(String sectionTitle, String headline, String body,
            List<String> banned) {
        try {
            String raw = llm.generate(
                    CompliancePrompts.SYSTEM,
                    CompliancePrompts.REWRITE_TEMPLATE.formatted(
                            String.join(", ", banned), headline, body));
            String[] parsed = parse(raw, headline);
            String newHeadline = parsed[0];
            String newBody = parsed[1];
            if (newBody.isBlank()) {
                log.warn("[{}] rewrite of \"{}\" returned no body, keeping original",
                        sectionTitle, headline);
                return null;
            }
            if (!bannedIn(newHeadline + "\n" + newBody).isEmpty()) {
                log.warn("[{}] rewrite of \"{}\" still contains banned wording, keeping original",
                        sectionTitle, headline);
                return null;
            }
            if (!numberTokens(headline + "\n" + body)
                    .containsAll(numberTokens(newHeadline + "\n" + newBody))) {
                log.warn("[{}] rewrite of \"{}\" introduced figures not in the original, "
                        + "keeping original", sectionTitle, headline);
                return null;
            }
            return new String[] {newHeadline, newBody};
        } catch (Exception e) {
            log.warn("[{}] rewrite call failed for \"{}\": {}",
                    sectionTitle, headline, e.toString());
            return null;
        }
    }

    /**
     * Parses the plain-text protocol ("line 1 = headline, blank line, body"),
     * tolerating fences and prefixes like the generation agent does. A
     * paragraph-length first line is body prose — it is reclaimed and the
     * original headline kept.
     */
    private static String[] parse(String raw, String fallbackHeadline) {
        String text = raw == null ? "" : raw.strip();
        text = text.replaceAll("(?m)^```[a-zA-Z]*\\s*$", "").strip();

        String headline = text;
        String body = "";
        int firstBreak = text.indexOf('\n');
        if (firstBreak >= 0) {
            headline = text.substring(0, firstBreak);
            body = text.substring(firstBreak + 1);
        }

        headline = headline.strip()
                .replaceFirst("(?i)^(headline|title)\\s*:\\s*", "")
                .replaceFirst("^[#*\\-\\s]+", "")
                .replaceAll("[*_]+$", "")
                .strip();
        if (headline.length() >= 2 && headline.startsWith("\"") && headline.endsWith("\"")) {
            headline = headline.substring(1, headline.length() - 1).strip();
        }
        body = body.strip().replaceAll("\\n{3,}", "\n\n");

        if (headline.length() > MAX_HEADLINE_CHARS) {
            body = body.isBlank() ? headline : headline + "\n\n" + body;
            headline = "";
        }
        if (headline.isBlank()) {
            headline = fallbackHeadline;
        }
        return new String[] {headline, body};
    }

    private static Set<String> numberTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            tokens.add(m.group().replace(",", ""));
        }
        return tokens;
    }
}
