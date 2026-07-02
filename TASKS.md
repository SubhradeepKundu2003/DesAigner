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

**Not done:** Agent #1 automated unit tests; DB persistence of results (in-memory
only, returned in HTTP response); Agents #2–#10.

**Next up → Agent #2 (Content Understanding):** first agent that calls the LLM.
Reads the `List<DocumentModel>` from `PipelineContext`, identifies projects /
achievements / events / metrics / announcements / milestones, classifies into
business categories, de-duplicates, and appends structured `ContentItem`s back to
the context. Lives in a new `agent/understanding/` package. Add the Spring AI
Ollama starter + a provider-agnostic LLM interface as part of this.

---

## Phase 0 — Foundation & Setup

- [x] Decide LLM provider + framework → **Ollama (local) + Spring AI**.
- [ ] Add Spring AI BOM + `spring-ai-starter-model-ollama` to `pom.xml`.
- [ ] Configure Ollama in `application.yaml`: base URL (`http://localhost:11434`),
      chat model (`qwen3.5:4b`), embedding model (`nomic-embed-text`).
- [ ] Verify Ollama is running (`ollama serve`) and the app can reach it (health check).
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

### 3.2 Content Understanding Agent
- [ ] LLM prompt to identify projects, achievements, events, metrics, announcements,
      milestones.
- [ ] Classify into business categories (Project Updates, Awards & Recognition,
      Training & Learning, Delivery Highlights, Customer Success, Technology
      Initiatives, Events).
- [ ] De-duplicate overlapping items.
- [ ] Output structured `ContentItem`s with source references preserved.

### 3.3 Content Planning Agent
- [ ] Score/prioritize items by impact.
- [ ] Select which items make the issue; drop or defer the rest.
- [ ] Order sections (Leadership Message → Delivery Highlights → Innovation
      Spotlight → Customer Success → Awards & Recognition → Upcoming Events).
- [ ] Produce a `NewsletterPlan` (section list + assigned content items).

### 3.4 Content Generation Agent
- [ ] Generate article body, titles, summaries, captions, callouts per section.
- [ ] Rewrite technical content into reader-friendly corporate tone.
- [ ] Keep tone/style consistent across sections (shared system prompt).

### 3.5 Fact Validation Agent
- [ ] Verify numbers, dates, names against source documents (provenance check).
- [ ] Flag missing / inconsistent / unsupported claims as `ValidationFlag`s.
- [ ] Gate: block export while unresolved high-severity flags exist (configurable).

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
