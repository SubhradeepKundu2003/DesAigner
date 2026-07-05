package com.tcs.contentGenerator.agent.review;

import java.util.List;

/**
 * The quality pass over a finished {@link com.tcs.contentGenerator.design.DesignDocument}:
 * every layout lint and editorial finding, plus a deterministic 0-100 quality
 * score (100 minus a per-finding severity penalty — the LLM proposes findings,
 * Java always computes the score, same split as every other agent's gate).
 */
public record ReviewReport(int qualityScore, List<ReviewFinding> findings, int componentsChecked,
        int articlesChecked) {

    public ReviewReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public long layoutFindingCount() {
        return findings.stream().filter(f -> f.source() == FindingSource.LAYOUT).count();
    }

    public long editorialFindingCount() {
        return findings.stream().filter(f -> f.source() == FindingSource.EDITORIAL).count();
    }
}
