# TASKS — AI-Powered Multi-Agent TD Monthly Newsletter Generator

> Living task file. Check items off as they land. Phases are ordered so each one
> produces something runnable. Brainstorm notes / open questions are at the bottom.

## Project Context

- **Stack:** Spring Boot 4.1.0, Java 21, Maven
- **Persistence:** Spring Data JPA + PostgreSQL
- **Security:** Spring Security (starter present, not yet configured)
- **Utilities:** Lombok
- **AI layer:** **Ollama (local, free, offline)** via Spring AI — no API key, no
  data leaves the machine (solves corporate data-sensitivity concerns).
  - Installed: Ollama `0.30.11`.
  - Chat model available: `qwen3.5:4b` (small — fine for prototype; consider a
    larger model, e.g. `qwen2.5:14b` / `llama3.1:8b`, for Content Understanding,
    Fact Validation, and Review once the pipeline works).
  - Embedding model available: `nomic-embed-text` (use for de-duplication,
    classification by similarity, and provenance/fact-check retrieval).
  - Keep all model calls behind a provider-agnostic interface so a cloud model
    (Anthropic/Gemini/etc.) can be swapped in later via config only.
- **Group / base package:** `com.tcs.contentGenerator`

The system turns uploaded business documents (Excel, Word, PDF, PowerPoint)
into a professionally designed monthly newsletter via a pipeline of specialized
agents coordinated by an orchestrator.

---

## Current State (handoff — read this first)

**Done & verified:** Agent #1 (Document Ingestion) is built, compiles, boots
against local Postgres, and is smoke-tested live. `POST /api/ingest` (multipart,
field `files`) accepts xlsx/docx/pdf/pptx, extracts them into the shared
`document/` model, stores originals + images under `storage/jobs/{jobId}/...`,
and returns a per-document block-count summary.

Agent #2 (Content Understanding) is built, compiles, and is **verified live
end-to-end** through the real Spring app (booted on port 8091 against local
Postgres + Ollama): `POST /api/ingest` on a sample `.docx` returned a correctly
classified `contentItems` array (title, category, type, metrics, provenance) —
not just a direct Ollama probe. Also verified via the temporary Thymeleaf UI
(`GET /` upload form → `POST /run` → `result.html` shows both agents' output).

Agent #3 (Content Planning) is built, compiles, and is **verified live e2e**
(booted on port 8091, Postgres + Ollama): `POST /api/ingest` on the sample
`.docx` returned a `plan` block — issue title "TD Monthly Newsletter — July
2026", a Leadership Message placeholder section, and Delivery Highlights with
the item scored 9/10 plus rationale. Also verified through the web UI
(`POST /run` → result page renders the plan) and the error page renders for
`Accept: text/html`.

**Agent #3 facts:**
- Package `agent/planning/`: `ContentPlanningAgent` (`@Order(3)`),
  `NewsletterSection` enum (declaration order = final reading order; maps 1:1
  from `BusinessCategory`; `LEADERSHIP_MESSAGE` has no source category and is
  planned with an empty item list for the generation agent to write;
  `IN_OTHER_NEWS` catches `OTHER`), `PlannedItem` (item + score 1–10 +
  rationale), `SectionPlan`, `NewsletterPlan` (issueTitle, sections in order,
  deferredItems), `ScoredItem` (LLM DTO — bare array, per the extractJson
  lesson), `PlanningPrompts`.
- Design: **one LLM call scores all items** (numbered list in, `ScoredItem[]`
  out, matched back by index, clamped 1–10); selection/ordering is
  **deterministic Java** — score < `app.planning.min-score` (default 4) →
  deferred; per-section cap `app.planning.max-items-per-section` (default 5),
  overflow deferred; sections emitted in `NewsletterSection` enum order. If the
  scoring call fails entirely, every item gets a neutral score 5 so the run
  still produces a plan.
- `PipelineContext` now carries `NewsletterPlan` (get/setNewsletterPlan; null
  until agent #3 runs). `IngestionResponse` gained a `plan` field
  (PlanSummary → SectionSummary/PlannedItemSummary).
- Note: the small model sometimes returns an empty `rationale` — handled
  (nulls stripped to ""), don't rely on it downstream.

Agent #4 (Content Generation) is built, compiles, and is **verified live e2e**
(booted on port 8091, Postgres + Ollama): `POST /api/ingest` on the small sample
`.docx` returned a `newsletter` block — Leadership Message written from an issue
digest (3 paragraphs, fallback headline) and a Delivery Highlights article with
a fresh headline, the NPS 72 metric woven into the prose, and `sourceTitle`
provenance back to the planned item.

**Agent #4 facts:**
- Package `agent/generation/`: `ContentGenerationAgent` (`@Order(4)`),
  `GeneratedNewsletter` (issueTitle + sections, `articleCount()`),
  `GeneratedSection`, `GeneratedArticle` (headline, body, `source` PlannedItem —
  null only for the Leadership Message), `GenerationPrompts`.
- Design: **one LLM call per planned item** plus one for the Leadership Message
  (written from a digest of section titles + top story titles, since it has no
  source items). A single shared system prompt carries the house tone.
- **Output protocol is plain text, not JSON** — "line 1 = headline, blank line,
  body paragraphs". Long prose inside JSON strings is where this small model
  reliably breaks (unescaped newlines), so generation avoids JSON entirely.
  `parse()` tolerates markdown fences, "Headline:" prefixes, quotes/`#`
  decoration, and — observed live — the model skipping the headline and opening
  with prose: a first line > 120 chars is reclaimed as body and the fallback
  headline (item title / "A Message from Leadership") takes over.
- Failure isolation per article: a failed call falls back to the item's
  extracted title + summary, so one bad generation degrades one article, never
  the issue. Bodies are plain paragraphs; `result.html` renders them with
  `white-space: pre-line` (`.article-body`).
- `PipelineContext` now carries `GeneratedNewsletter` (get/setGeneratedNewsletter;
  null until agent #4 runs). `IngestionResponse` gained a `newsletter` field
  (NewsletterSummary → GeneratedSectionSummary/ArticleSummary).

Agent #5 (Fact Validation) is built, compiles, and is **verified live e2e**
(booted on port 8091, Postgres + Ollama): `POST /api/ingest` on the small sample
`.docx` returned a `validation` block — 1 article fact-checked, 1 skipped (the
Leadership Message, no source by design), and on one run the checker caught a
genuinely hallucinated claim ("We are already preparing updates based on their
feedback" — HIGH severity, nothing in the source says that) which correctly
flipped `exportBlocked: true`. The web UI (`POST /run`) renders the Agent #5
stage with the export-gate banner (a second run produced zero flags → "clear
for export" branch verified too; flag output varies run to run, small model).

**Agent #5 facts:**
- Package `agent/validation/`: `FactValidationAgent` (`@Order(5)`),
  `ValidationSeverity` (LOW→MEDIUM→HIGH, lenient `fromLabel` falls back to
  MEDIUM, `meetsOrExceeds` for the gate), `ValidationFlag` (section + article
  headline + claim + severity + issue), `ValidationReport` (flags, checked,
  skipped, exportBlocked), `ClaimFlag` (LLM DTO — bare array, per the
  extractJson lesson), `ValidationPrompts`, `SourceTextResolver`.
- Provenance resolution: `ContentItem.sources()` refs are **document/chunk
  level**, not block level — the understanding agent stamps
  `SourceRef(filename, "document" | "chunk i/n", itemCounter)`. So
  `SourceTextResolver` finds the `DocumentModel` by filename and **re-chunks it
  with the same deterministic `DocumentChunker`** to return exactly the chunk
  the item came from, capped at `app.validation.max-source-chars` (default
  6000 — keeps source + article inside `num-ctx: 8192`).
- Two independent checks per article: (1) a **deterministic numeric
  cross-check** (regex `\d[\d,]*(\.\d+)?`, comma-stripped token + substring
  match against source + item title/summary/metrics + issue title; misses →
  MEDIUM, no LLM cost); (2) **one LLM call per article** returning a bare
  `ClaimFlag[]` (empty array = all supported), severities parsed leniently.
- Deterministic Java gate: `exportBlocked` = any flag `meetsOrExceeds`
  `app.validation.blocking-severity` (default `high`).
- Failure isolation: LLM-call failure or unresolvable source → one LOW flag on
  that article, run continues. Leadership Message skipped (source == null).
- `PipelineContext` carries `ValidationReport`; `IngestionResponse` gained a
  `validation` field (ValidationSummary → FlagSummary). `result.html` renders
  the gate banner (`.gate.ok`/`.gate.blocked`) + flags table with severity
  chips (`.sev.high/.medium/.low` in `app.css`).
- Observed live: the small model's flags lean verbose/over-cautious (it flags
  interpretive framing as MEDIUM); severities other than HIGH don't block, so
  this is noise, not a gate problem. Tune `ValidationPrompts.SYSTEM` if it gets
  worse.

**Thymeleaf is now properly integrated (no longer "temporary"):**
- Shared fragments in `templates/fragments/page.html` (`head(title)` fragment +
  `back` link); all styling moved to `static/css/app.css`; `templates/error.html`
  replaces the whitelabel error page (`server.error.include-message: always`
  set for dev). `result.html` shows all three agents' output; `index.html`
  lists the three-agent chain. Thymeleaf stays as the templating engine for the
  Layout & Design agent's HTML newsletter later (see pom comment).

**AI layer facts:**
- Provider-agnostic LLM boundary lives in `llm/`: `LlmClient` interface
  (`generate(system,user)` + `generate(system,user,Class<T>)` structured) and
  `SpringAiLlmClient` (wraps Spring AI `ChatClient`; injects the autoconfigured
  `ChatModel`). Agents depend only on `LlmClient` — swap providers via config.
- Spring AI **2.0.0** (BOM import in `pom.xml`, aligns with Boot 4.x);
  `spring-ai-starter-model-ollama`. Config in `application.yaml` under `spring.ai.ollama`
  (base-url, chat model `qwen3.5:4b`, embed model `nomic-embed-text`,
  `init.pull-model-strategy: never`).
- **`spring.ai.ollama.chat.think: false`** — qwen3.5 is a reasoning model;
  thinking + JSON was ~6x slower (148s vs 24s/doc) with no quality gain. This
  property is the only clean lever (`OllamaOptions` has no `think` setter).
  Verified binding live: warm calls now complete in ~30s.
- **Structured output cannot rely on Spring AI's `.entity()`/`BeanOutputConverter`
  cleanup alone** — this local model does not reliably return clean JSON. Observed
  live: (a) a bare JSON array instead of the requested wrapper object → target
  type changed to `ExtractedItem[]`; (b) the raw completion sometimes contains
  *two* JSON payloads back-to-back (one bare, one re-stated inside a ```json
  fence) → a naive "first `[` to last `]`" span swallows the fence in between and
  breaks parsing. Fixed in `SpringAiLlmClient.extractJson` with a string-aware
  bracket-depth scanner that returns only the *first complete* JSON value and
  ignores anything after it. This lives in the provider wrapper, so every
  structured-output agent benefits, not just Agent #2.
- `PipelineContext` carries `List<ContentItem>` (add/get). `POST /api/ingest`
  runs the whole chain; the response includes `contentItems` and `plan`.
- **Web UI:** `PipelineService` (in `orchestrator/`) is the shared store-and-run
  path used by both `IngestionController` (JSON API) and `PipelineViewController`
  (`web/`) + `templates/index.html` (upload form) / `templates/result.html`
  (all agents' output). `spring-boot-starter-thymeleaf` in `pom.xml`.

**Key facts for continuing:**
- App runs on a user-managed instance (port 8090 in last session). Local Postgres
  DB `contentgenerator`, user `postgres`, password `root` (env-overridable:
  `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`). Build: `./mvnw compile`.
- Pipeline contract lives in `orchestrator/`: `Agent` interface
  (`name()` + `execute(PipelineContext)`), `AgentOrchestrator` (runs all `Agent`
  beans by `@Order`), `PipelineContext` (holds `jobId`, input files, and the
  `List<DocumentModel>` produced by ingestion — later agents append their output).
- Shared model in `document/`: `DocumentModel` = metadata + `List<DocumentBlock>`;
  `DocumentBlock` sealed → `HeadingBlock | TextBlock | TableBlock | ImageBlock`,
  each with a `SourceRef` (doc + sheet/page/slide + ordinal) for provenance.
- **Each agent = its own package** under `agent/`. Ingestion is `agent/ingestion/`.
- **AI not wired yet** — Spring AI + Ollama starter still needs adding (Phase 0).

**Not done:** Agents #1–#5 automated unit tests; DB persistence of results
(in-memory only, returned in HTTP response); flag *resolution* (the gate reports
`exportBlocked` but there is no endpoint to resolve/waive flags yet — belongs
with Phase 4 API work); Agents #6–#10.

**Performance note (observed live):** with `num-ctx: 8192` and chunking, a large
docx (93 blocks → 2 chunks) took ~9.5 min *per understanding chunk* on this
CPU-only machine — decode of a 20-item JSON array dominates. The small sample
docx runs the whole 4-agent pipeline in ~90s. Generation calls are short
(~150-word outputs, ~25s each). Budget accordingly when testing with the big
`samples/TD_Monthly_SourcePack_June2026.docx`.

**Next up → Agent #6 (Brand Compliance):** apply writing guidelines + approved
terminology from a rules config file (start simple: a YAML/properties list of
banned terms → preferred replacements, casing rules for product/brand names),
check the generated articles, report violations and auto-fix where safe
(deterministic string checks first; LLM only if a rewrite is needed). New
`agent/compliance/` package. LLM-output lessons so far: lists → bare arrays
(`Foo[]`), long prose → plain text protocol (see Agent #4 facts), optional
string fields may come back empty — always null-guard.

---

## Phase 0 — Foundation & Setup

- [x] Decide LLM provider + framework → **Ollama (local) + Spring AI**.
- [x] Add Spring AI BOM + `spring-ai-starter-model-ollama` to `pom.xml` (Spring AI 2.0.0).
- [x] Configure Ollama in `application.yaml`: base URL (`http://localhost:11434`),
      chat model (`qwen3.5:4b`), embedding model (`nomic-embed-text`), `think: false`.
- [x] Verify Ollama is running and reachable — confirmed via a full live
      `POST /api/ingest` run through the booted Spring app (not just a direct
      Ollama probe): `qwen3.5:4b` returned correctly classified content items.
- [ ] Add document-parsing deps: Apache POI (xlsx/docx/pptx), Apache PDFBox (pdf),
      optionally Apache Tika as a unified fallback extractor.
- [ ] Add a PDF/HTML rendering dep for export (e.g. openhtmltopdf / Flying Saucer),
      and a templating engine (Thymeleaf) for HTML newsletter layout.
- [ ] Externalize secrets/config: DB creds (and Ollama URL) via env vars in
      `application.yaml` — no API key needed for local Ollama.
- [ ] Configure PostgreSQL datasource + JPA (ddl-auto, dialect) in `application.yaml`.
- [ ] Set up local Postgres (Docker Compose file) for dev.
- [ ] Baseline Spring Security config (permit dev endpoints, lock the rest) so the
      app boots without blocking API work.
- [ ] Confirm `mvnw clean verify` runs green.

## Phase 1 — Domain Model & Persistence

- [ ] Define core entities: `NewsletterJob` (a generation run), `SourceDocument`,
      `ExtractedContent`, `ContentItem` (classified unit), `NewsletterSection`,
      `NewsletterArticle`, `ValidationFlag`, `GeneratedNewsletter`.
- [ ] Define a `JobStatus` enum tracking pipeline stage + per-agent status.
- [ ] Define the **internal document model** (common normalized format: blocks of
      text, tables, images, metadata) that every downstream agent consumes.
- [ ] JPA repositories for each aggregate root.
- [ ] Store uploaded files (filesystem/object store now; abstract behind an interface
      so it can move to S3/SharePoint later).

## Phase 2 — Orchestrator & Agent Contract

- [ ] Define an `Agent` interface / contract: `input → output`, with typed context.
- [ ] Define a shared `PipelineContext` object passed through the chain, holding the
      accumulating newsletter state + provenance (which source each fact came from).
- [ ] Implement `AgentOrchestrator` that runs agents in sequence, persists status
      after each stage, and supports resume / re-run of a single stage.
- [ ] Error handling: per-agent failure isolation, retry policy, and a human-review
      hold state.
- [ ] Emit progress events (for later: WebSocket/SSE to a UI).

## Phase 3 — Agents (build in pipeline order)

### 3.1 Document Ingestion Agent  — DESIGN LOCKED
**Decisions:** per-type libraries (POI + PDFBox, no Tika); extract text + tables +
images + metadata; store originals & extracted images on local filesystem behind a
`StorageService` interface (swap to S3/SharePoint later).

**Package layout**
- `document/` — SHARED internal model (sealed records): `DocumentModel`,
  `DocumentMetadata`, `DocumentType`, `SourceRef`, and
  `DocumentBlock` → `HeadingBlock | TextBlock | TableBlock | ImageBlock`.
  Every block carries a `SourceRef` (file + sheet/page/slide + position) for
  later provenance/fact-checking.
- `storage/` — `StorageService` interface + `LocalFileStorageService` (configurable
  root dir); `ImageBlock` holds a `storedRef`, never raw bytes.
- `agent/ingestion/` — `DocumentIngestionAgent` (implements `Agent`),
  `DocumentExtractor` interface, `ExtractorRegistry` (injects
  `List<DocumentExtractor>`, matches by type — no switch), and
  `extractor/`: `ExcelExtractor`, `WordExtractor`, `PdfExtractor`,
  `PowerPointExtractor`.

**Tasks**
- [x] Add deps: Apache POI (`poi`, `poi-ooxml`), Apache PDFBox.
- [x] Build the shared `document/` model (sealed interface + records).
- [x] `StorageService` + `LocalFileStorageService` (+ config for root dir).
- [x] `DocumentExtractor` interface + `ExtractorRegistry`.
- [x] `ExcelExtractor` — sheets → `TableBlock`s, cell text, embedded pictures.
- [x] `WordExtractor` — paragraphs/headings, tables, embedded pictures.
- [x] `PdfExtractor` — text per page, images per page (tables best-effort).
- [x] `PowerPointExtractor` — per-slide text boxes, tables, pictures.
- [x] `DocumentIngestionAgent` — accept `.xlsx/.docx/.pdf/.pptx`, delegate via
      registry, emit `List<DocumentModel>` into `PipelineContext`.
- [x] Multipart upload endpoint (`POST /api/ingest`) + dev `SecurityConfig`.
- [x] `AgentOrchestrator` runner (Phase 2 minimal contract in place).
- [ ] Unit tests with a sample fixture per file type (assert blocks + SourceRefs).
- [x] Run end-to-end against a real sample document (manual smoke test) —
      xlsx (2 sheets → 2 tables) and docx (H1/H2 + 2 paras + 1 table → 2 headings,
      2 text, 1 table) both returned HTTP 200 with correct block counts; input
      files persisted under `storage/jobs/{jobId}/inputs/`.

### 3.2 Content Understanding Agent  — DONE (live e2e verified)
**Package `agent/understanding/`:** `ContentUnderstandingAgent` (`@Order(2)`),
`ContentItem` (record) + `BusinessCategory`/`ItemType` enums (lenient parsing),
`DocumentTextRenderer` (blocks→plain text, table rows capped), `ExtractedItem`
(LLM JSON DTO — the model is asked for a bare array of these, not a wrapper
object), `UnderstandingPrompts`, `ContentDeduplicator` (title-normalized merge,
unions sources/metrics — semantic/embedding dedup is a later upgrade).
Per-document LLM failures are caught and skipped so one bad doc can't abort a run.

- [x] LLM prompt to identify projects, achievements, events, metrics, announcements,
      milestones.
- [x] Classify into business categories (Project Updates, Awards & Recognition,
      Training & Learning, Delivery Highlights, Customer Success, Technology
      Initiatives, Events).
- [x] De-duplicate overlapping items (heuristic; embedding-based dedup later).
- [x] Output structured `ContentItem`s with source references preserved.
- [x] Live end-to-end run through the Spring app — `POST /api/ingest` on a real
      `.docx` produced a correctly classified item via the booted app + Ollama.
- [ ] Automated unit tests (currently verified manually only).

### 3.3 Content Planning Agent  — DONE (live e2e verified)
**Package `agent/planning/`:** `ContentPlanningAgent` (`@Order(3)`) — one LLM
call scores every item (bare `ScoredItem[]` array, matched by index, neutral
fallback score on failure); deterministic Java does selection (min-score
threshold), per-section caps, and canonical `NewsletterSection` ordering; output
is a `NewsletterPlan` on the context (sections + deferred items). Thresholds
config-driven under `app.planning.*`.

- [x] Score/prioritize items by impact (LLM 1–10 score + rationale per item).
- [x] Select which items make the issue; drop or defer the rest (threshold +
      per-section cap; deferred items kept on the plan for transparency).
- [x] Order sections (Leadership Message → Delivery Highlights → Project
      Updates → Innovation Spotlight → Customer Success → Awards & Recognition
      → Training & Learning → Upcoming Events → In Other News).
- [x] Produce a `NewsletterPlan` (section list + assigned content items).
- [x] Live end-to-end run through the Spring app — sample `.docx` produced a
      correct plan via API and web UI.
- [ ] Automated unit tests (currently verified manually only).

### 3.4 Content Generation Agent  — DONE (live e2e verified)
**Package `agent/generation/`:** `ContentGenerationAgent` (`@Order(4)`) — one
plain-text LLM call per planned item ("line 1 = headline, rest = body"; JSON
deliberately avoided for long prose) plus one Leadership Message call from an
issue digest; shared system prompt for tone; per-article fallback to the
extracted title/summary on failure. Output is a `GeneratedNewsletter` on the
context.

- [x] Generate article body + headline per planned item, per section.
- [x] Write the Leadership Message (no source items — issue-digest prompt).
- [x] Rewrite technical content into reader-friendly corporate tone.
- [x] Keep tone/style consistent across sections (shared system prompt).
- [x] Live end-to-end run through the Spring app — sample `.docx` produced the
      written issue via API (and web UI).
- [ ] Automated unit tests (currently verified manually only).
- [ ] (Later) captions/callouts once the Layout agent needs them.

### 3.5 Fact Validation Agent  — DONE (live e2e verified)
**Package `agent/validation/`:** `FactValidationAgent` (`@Order(5)`) — per
article: deterministic numeric cross-check + one LLM fact-check call (bare
`ClaimFlag[]`) against source text resolved via `SourceTextResolver`
(re-chunks the source doc deterministically, since provenance is chunk-level).
Deterministic Java gate on `app.validation.blocking-severity` (default high).

- [x] Verify numbers, dates, names against source documents (provenance check).
- [x] Flag missing / inconsistent / unsupported claims as `ValidationFlag`s.
- [x] Gate: block export while unresolved high-severity flags exist (configurable).
- [x] Live end-to-end run through the Spring app — validation block returned
      via API (caught a real hallucinated claim, HIGH → export blocked) and
      the web UI renders the gate banner + flags table.
- [ ] Flag resolution/waiver endpoint (Phase 4, needed before export can unblock).
- [ ] Automated unit tests (currently verified manually only).

### 3.6 Brand Compliance Agent
- [ ] Apply writing guidelines + approved terminology (rules config file to start).
- [ ] Enforce formatting standards, approved colors/fonts/logos.
- [ ] Report violations + auto-fix where safe.

### 3.7 Layout & Design Agent
- [ ] Select a newsletter template.
- [ ] Arrange text, tables, charts, images into the template; balance hierarchy.
- [ ] Produce a render-ready HTML/Thymeleaf model.

### 3.8 Image & Graphics Agent
- [ ] Select relevant images from an approved repository.
- [ ] (Optional / later) generate AI illustrations; create icons/callouts.
- [ ] Optimize image size/quality for the output format.

### 3.9 Review Agent
- [ ] Grammar/spelling/readability checks over the assembled newsletter.
- [ ] Validate formatting + branding consistency end-to-end.
- [ ] Assign a quality score + itemized findings.

### 3.10 Export & Publishing Agent
- [ ] Export to **PDF** (primary), **HTML**, **Word (.docx)**, **PowerPoint (.pptx)**.
- [ ] Package for email distribution / internal portal.
- [ ] Persist the final artifact + make it downloadable.

## Phase 4 — API & (optional) UI

- [ ] REST endpoints: create job, upload docs, trigger pipeline, poll status,
      fetch/download result, resolve validation flags.
- [ ] DTOs + validation on all inputs.
- [ ] Progress streaming (SSE/WebSocket).
- [ ] Minimal web UI for upload → progress → review flags → download (later).

## Phase 5 — Quality, Ops, Hardening

- [ ] Unit + integration tests per agent; end-to-end pipeline test with fixtures.
- [ ] Prompt regression tests (golden outputs) for LLM agents.
- [ ] Observability: structured logging, per-agent timing, token/cost tracking.
- [ ] Rate limiting / retry / backoff for LLM calls.
- [ ] Security review: file-upload validation, auth on endpoints, PII handling.
- [ ] Config profiles (dev/prod), secrets management.

---

## Future Enhancements (from spec — not scheduled yet)

- Personalized newsletters per business unit.
- Multilingual generation.
- SharePoint / Teams / Outlook integration.
- Auto-retrieval of project metrics from enterprise systems.
- AI executive summaries + trend analysis.
- Reader-engagement analytics.

---

## Open Questions / To Brainstorm

- [ ] **Agent framework:** hand-rolled orchestrator vs. Spring AI advisors/tools vs.
      a full agent framework? (Leaning: hand-rolled orchestrator, Spring AI for the
      model calls — simplest and most controllable.)
- [x] **LLM provider** → Ollama (local). Resolves data sensitivity (nothing leaves
      the machine). `qwen3.5:4b` for prototype; upsize models as quality demands.
- [ ] Are source documents free-form, or is there a known monthly template we can
      exploit for more reliable extraction?
- [ ] Where do approved brand assets (logos, colors, image repo) live today?
- [ ] Which export format is the real deliverable vs. nice-to-have? (Assumed: PDF
      primary.)
- [ ] Human-in-the-loop: is review/approval mandatory before publish, or fully auto?
- [ ] Volume/scale: how many source docs per issue, how large?
