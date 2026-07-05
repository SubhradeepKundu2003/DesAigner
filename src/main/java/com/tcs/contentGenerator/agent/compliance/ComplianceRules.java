package com.tcs.contentGenerator.agent.compliance;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The brand rulebook, bound from {@code app.compliance.rules} in
 * {@code application.yaml} so editors can change the rules without touching
 * code. Three rule kinds, matching the three {@link ViolationType}s:
 *
 * <ul>
 *   <li>{@code terminology} — banned term → approved replacement, fixed by
 *       deterministic case-preserving substitution;</li>
 *   <li>{@code proper-names} — canonical spellings of brand/product names,
 *       any other casing is corrected to the canonical form;</li>
 *   <li>{@code banned-phrases} — wording with no drop-in replacement (e.g.
 *       buzzwords); an article containing one gets a single LLM rewrite.</li>
 * </ul>
 *
 * All matching is whole-word and case-insensitive (except proper names, where
 * the casing difference IS the violation).
 */
@ConfigurationProperties("app.compliance.rules")
public record ComplianceRules(
        Map<String, String> terminology,
        List<String> properNames,
        List<String> bannedPhrases) {

    public ComplianceRules {
        terminology = terminology == null ? Map.of() : Map.copyOf(terminology);
        properNames = properNames == null ? List.of() : List.copyOf(properNames);
        bannedPhrases = bannedPhrases == null ? List.of() : List.copyOf(bannedPhrases);
    }
}
