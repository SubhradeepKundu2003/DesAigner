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

**Deliverable (decided 2026-07-04): an *editable* newsletter, not email HTML.**
The pipeline produces a renderer-independent **Design Model** (JSON design
tree — pages, positioned text/image/shape components, theme). That model is
the single source of truth: a future Angular visual editor (Canva /
PowerPoint-Online-style) loads and edits it, and dedicated renderer classes
export it as **PPTX, PDF, and HTML**. See "Design Model architecture" before
§3.7.

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

Agent #6 (Brand Compliance) is built, compiles, has **unit tests** (the first
in the repo — `BrandComplianceAgentTest`, 6 tests, LLM stubbed, all green via
`./mvnw test -Dtest=BrandComplianceAgentTest`), and is **verified live e2e**
(booted on port 8091, Postgres + Ollama): a clean run returned
`compliance: {articlesChecked: 2, violations: []}`; a second run with a
casing rule injected via `SPRING_APPLICATION_JSON` (canonical `APOLLO`) caught
"Apollo" in both articles, auto-fixed them (`articlesFixed: 2`, headline and
body now say APOLLO), and the web UI rendered the Agent #6 stage with the
violations table and "✓ fixed" chips.

**Agent #6 facts:**
- Package `agent/compliance/`: `BrandComplianceAgent` (`@Order(6)`),
  `ComplianceRules` (`@ConfigurationProperties("app.compliance.rules")` record
  — the app class now has `@ConfigurationPropertiesScan`), `ViolationType`
  (TERMINOLOGY / CASING / BANNED_PHRASE), `ComplianceViolation`,
  `ComplianceReport` (violations + articlesChecked/articlesFixed +
  `unresolvedCount()`), `CompliancePrompts`.
- The rulebook lives in `application.yaml` under `app.compliance.rules`:
  `terminology` (banned term → replacement; **map keys use `"[bracket]"`
  notation** or Spring's relaxed binding strips spaces from keys like
  `"in order to"`), `proper-names` (canonical casing), `banned-phrases`
  (buzzwords with no drop-in replacement).
- Fix strategy, cheapest first: (1) terminology → deterministic case-preserving
  substitution (ALL-CAPS match → ALL-CAPS replacement, Capitalized →
  Capitalized); (2) casing → deterministic correction to canonical; (3) banned
  phrases → **one LLM rewrite call per affected article** (plain-text
  headline/body protocol, same as generation), accepted only if the banned
  wording is actually gone, the body survived parsing, AND the rewrite's
  number tokens ⊆ the original's (fact validation already ran — a rewrite must
  not invent figures behind its back). Rejected/failed rewrite → article keeps
  its deterministically-fixed text, violation reported `fixed: false` ("needs
  editor" in the UI). All matching is whole-word regex, patterns compiled once
  in the constructor.
- The agent **replaces** the `GeneratedNewsletter` on the context with the
  corrected articles (so the Agent #4 stage in `result.html` shows post-fix
  text); the `ComplianceReport` is the record of what changed. No export gate
  — breaches are editorial, not factual.
- `PipelineContext` carries `ComplianceReport`; `IngestionResponse` gained a
  `compliance` field (ComplianceSummary → ViolationSummary). `result.html`
  renders the stage (stats line + violations table with `.fix.ok`/`.fix.pending`
  chips in `app.css`); `index.html` lists agent #6.
- Live-test trick: rules are config, so a guaranteed-hit rule can be injected
  without code changes via `SPRING_APPLICATION_JSON` (note:
  `-Dspring-boot.run.arguments=--a,--b` comma-splitting did NOT work with this
  plugin version — the whole string landed in the first property).

**Thymeleaf is now properly integrated (no longer "temporary"):**
- Shared fragments in `templates/fragments/page.html` (`head(title)` fragment +
  `back` link); all styling moved to `static/css/app.css`; `templates/error.html`
  replaces the whitelabel error page (`server.error.include-message: always`
  set for dev). `result.html` shows all agents' output; `index.html` lists the
  agent chain. Thymeleaf serves the **dev web UI only** — the newsletter itself
  is produced by the Design Model + renderers (see §3.7+), not by Thymeleaf
  templates (the pom comment saying otherwise predates this decision).

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

**Not done:** Agents #1–#5 automated unit tests (#6/#7/renderers/stores have
them); Agents #8–#9. (DB persistence of the design + flags and the
flag-resolution endpoint landed with Phase C — see below; intermediate agent
outputs other than flags are still in-memory/response-only by design.)

**Performance note (observed live):** with `num-ctx: 8192` and chunking, a large
docx (93 blocks → 2 chunks) took ~9.5 min *per understanding chunk* on this
CPU-only machine — decode of a 20-item JSON array dominates. The small sample
docx runs the whole 4-agent pipeline in ~90s. Generation calls are short
(~150-word outputs, ~25s each). Budget accordingly when testing with the big
`samples/TD_Monthly_SourcePack_June2026.docx`.

Agent #7 (Design Composition) is built, compiles, has **unit tests** (Jackson
round-trip on the shared `design/` model + `LayoutEngineTest`'s layout
invariants — no overlaps, frames inside margins, all articles placed,
pagination fires — 5 tests total, all green), and is **verified live e2e**
(booted on port 8090, Postgres + Ollama): `POST /api/ingest` on the sample
`.docx` returned `design: {pageCount: 1, componentCount: 12}` — a HERO
Leadership Message plus a STAT_CALLOUT Delivery Highlights article (the
`NPS:72` key metric correctly triggered the stat pattern, showing "72" /
"NPS:72" side by side). The web UI (`POST /run`) embeds the rendered HTML
live via an `<iframe srcdoc>` in the Agent #7 stage — Phase A's exit
criterion (the design visible end-to-end) is met.

**Agent #7 facts:**
- Shared **`design/`** package (peer of `document/`): sealed `Component`
  (`TextBox | ImageBox | ShapeBox`, `@JsonTypeInfo(use = DEDUCTION)` so the
  JSON round-trips without a synthetic type field — Jackson infers the
  subtype from each record's distinct field set), `Frame`, `ComponentRole`,
  `SourceLink` (section title + article headline — the same natural-key
  provenance pattern validation/compliance already use, not a synthetic id),
  `TextStyle`, `PageSize`, `Spacing`, `Theme`, `Asset`, `Page`, `DesignMeta`,
  `DesignDocument` (schemaVersion, revision, meta, theme, assets, pages).
- Package `agent/design/`: `DesignCompositionAgent` (`@Order(7)`) —
  **pattern selection is rule-based, not an LLM call** (no cost at this
  stage): `LEADERSHIP_MESSAGE`→HERO, `UPCOMING_EVENTS`→EVENT_LIST, a single
  article with a numeric key metric→STAT_CALLOUT (value/label pulled via the
  same numeric regex fact-validation uses), exactly 2 articles→TWO_COLUMN,
  else STANDARD. `IconMatcher` deterministically maps each `NewsletterSection`
  to a theme color role (a colored dot stands in for real iconography until
  Agent #8 sources actual images). `TemplateCatalog` loads
  `DesignTemplate` (name + `Theme`) from
  `resources/design-templates/td-classic.json` (A4 portrait, one template).
  `CompositionPlan`/`SectionComposition` carry the semantic plan — **no
  coordinates yet**.
- Package `agent/design/layout/`: `LayoutEngine` (100% deterministic, no LLM)
  turns the `CompositionPlan` into positioned pages — one method per pattern,
  measuring text via `TextMeasurer` (AWT `FontMetrics` against a scratch
  `Graphics2D`, estimate only, every renderer wraps natively at render time)
  and tracking the cursor via `Paginator` (starts a new page when a component
  doesn't fit; `OverflowResolver` clamps a component's height to what a whole
  page could ever hold when it's bigger than that — logged, and by design the
  clamped component still visually overflows its frame until a human edits it
  in the future editor — an accepted v1 trade-off, not a bug).
- **Render `render/`**: `DesignRenderer` interface + `RendererRegistry`
  (same registry-over-a-list pattern as `ExtractorRegistry`) + `ExportFormat`
  (HTML implemented; PPTX/PDF are enum values only, Phase B). `render/html/HtmlDesignRenderer`
  emits a standalone HTML doc — one `div.page` per page, `pt`-unit
  absolutely-positioned `div.cmp` children (CSS `pt` *is* our model's unit,
  no conversion needed), theme colors/styles inlined. Deliberately
  print/preview-style, not responsive — this is not the editor.
- `PipelineContext` carries `DesignDocument` (get/setDesignDocument; null
  until agent #7 runs). `IngestionResponse` gained a `design` field
  (`DesignSummary(pageCount, componentCount)`). The web UI doesn't put the
  raw HTML through `IngestionResponse` (JSON API callers don't need a
  megabyte of markup) — `PipelineViewController` renders it separately via
  `HtmlDesignRenderer` into a `designHtml` model attribute, embedded through
  `<iframe th:attr="srcdoc=${designHtml}">` (Thymeleaf HTML-escapes the
  attribute value by default, which is exactly what a `srcdoc` needs).
- **Gotcha for future Jackson use anywhere in this repo:** this project is on
  **Jackson 3.x** (Spring Boot 4.1 pulls `tools.jackson.core:jackson-databind`,
  *not* `com.fasterxml.jackson.databind`) — the base package renamed from
  `com.fasterxml.jackson.*` to `tools.jackson.*` (annotations stayed at
  `com.fasterxml.jackson.annotation`, only core/databind moved). Import
  `tools.jackson.databind.ObjectMapper` / `tools.jackson.databind.json.JsonMapper`,
  not the 2.x package — the 2.x import compiles as a phantom-looking IDE
  error but is really just the wrong artifact.

**Phase B PPTX renderer DONE (2026-07-05):** `render/pptx/PptxDesignRenderer`
(POI XSLF) is built, unit-tested, and **verified live e2e** (booted on port
8090, Postgres + Ollama): `POST /run` on the sample docx → `GET
/design/{jobId}/export.pptx` downloaded a real `.pptx` (confirmed via `file`
and by parsing it back with POI) — 1 slide, page size 595×842pt matching the
theme, all 12 components present with correct text and positions, including
the STAT_CALLOUT "72"/"NPS:72" pair and multi-paragraph article bodies.

**Phase B facts:**
- No manual pt→EMU math needed: XSLF shape anchors (`Rectangle2D`) and
  `XMLSlideShow.setPageSize(Dimension)` are already expressed in points in the
  POI API — the same unit the design model uses — so `Frame` values pass
  straight through. (`Dimension` is int-only, so the theme's page size is
  rounded to the nearest point for the slide size.)
- `TextBox` → `XSLFTextBox`, one `XSLFTextParagraph`/run per `\n`-separated
  line (font/size/bold/color from the `TextStyle` the `styleRef` names,
  falling back to a default style same as the HTML renderer); insets zeroed
  via `Insets2D` (not `java.awt.Insets` — POI has its own type) to match the
  HTML renderer's zero-padding boxes.
- `ShapeBox` → `XSLFAutoShape` (`ShapeType.ELLIPSE` for `"circle"`, else
  `RECT`), filled from the theme color role, no line.
- `ImageBox` → real `XSLFPictureShape` when `assetId` resolves through
  `document.assets()` **and** `StorageService.retrieve()` succeeds (picture
  type sniffed from the stored ref's extension); otherwise a dashed
  placeholder `XSLFAutoShape` with centered alt-text, matching the HTML
  renderer's placeholder. Failure isolation: a `StorageService` failure is
  caught and logged, falling back to the placeholder rather than failing the
  whole export — since Agent #8 (graphics) doesn't exist yet, every
  `ImageBox` today takes the placeholder path; the real-picture path is
  exercised only by `PptxDesignRendererTest`.
- Dev-harness wiring (not the real Phase C editor API): `PipelineViewController`
  keeps an in-memory `Map<jobId, DesignDocument>` populated by `/run` and
  serves it from `GET /design/{jobId}/export.pptx` via `RendererRegistry`, so
  `result.html` can offer a "Download PPTX" link right next to the HTML
  preview. This cache is intentionally throwaway — real persistence
  (Postgres jsonb + revision) is still Phase C.
- Unit tests (`PptxDesignRendererTest`, 3 tests, all green): parses the
  rendered bytes back with POI and asserts on actual shapes (types, anchors,
  text, embedded picture bytes) rather than just checking for non-empty
  output; a fake `StorageService` also covers the retrieval-failure →
  placeholder fallback path.

**Phase B PDF renderer DONE (2026-07-05):** `render/pdf/PdfDesignRenderer` is
built, unit-tested, and **verified live e2e** (booted on port 8091, Postgres +
Ollama): `POST /run` on the small sample docx (~64s full pipeline) → `GET
/design/{jobId}/export.pdf` returned a real `application/pdf`; rasterized it
with `pdfbox-app` and visually confirmed the page matches the HTML preview —
blue serif issue title, circular section-icon dots, HERO leadership message,
divider, and the STAT_CALLOUT "72"/"NPS:72" pair, all positioned correctly on
a 595×842pt page.

**PDF renderer facts:**
- v1 strategy as planned: the PDF renderer feeds the **HTML renderer's output**
  through openhtmltopdf. Dependency is
  `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.40` — the **maintained
  fork on the PDFBox 3.x line** (matches our pdfbox 3.0.5; the original
  `com.openhtmltopdf` artifacts are stuck on PDFBox 2.x and would clash). The
  fork kept the original `com.openhtmltopdf.*` package names, and
  `useFastMode()` is deprecated there because fast mode is the default.
- `PdfDesignRenderer` calls `HtmlDesignRenderer.renderHtml(document, extraCss)`
  (new public hook; `render()` delegates to it with `""`) with a print
  stylesheet appended after the base rules: `@page { size: <theme page size>;
  margin: 0 }`, `.page { margin:0; box-shadow:none;
  page-break-before:always }` + `.page:first-child { page-break-before:auto }`
  (break-*before* so no trailing blank page — the unit test asserts *exact*
  page counts to guard that regression).
- `HtmlDesignRenderer` changes made for this (all no-ops in browsers):
  emits **well-formed XHTML** (`<meta …/>` self-closed) because openhtmltopdf
  parses strict XML; **font-family now appends a generic fallback**
  (`SansSerif,sans-serif` / `Serif,serif` via a name heuristic) so the PDF
  maps logical theme font names to Helvetica/Times instead of an arbitrary
  default; **circle border-radius is a pt length** (`min(w,h)/2`) instead of
  `50%`, which openhtmltopdf handles unreliably.
- Known cosmetic gap: the ImageBox placeholder centers its label with
  `display:flex`, which openhtmltopdf ignores (treated as block) — in the PDF
  the label sits top-left inside the dashed box. Placeholder-only; goes away
  when Agent #8 supplies real images.
- Dev-harness export endpoint generalized:
  `GET /design/{jobId}/export.{extension}` (pptx | pdf | html via
  `ExportFormat.valueOf`, 404 on unknown extension), correct Content-Type per
  format; `result.html` now has a "Download PDF" link next to the PPTX one.
- Unit tests (`PdfDesignRendererTest`, 3 tests, all green; whole design/render
  suite 17/17): parse the rendered bytes back with PDFBox `Loader`, assert
  media box 595×842pt, exact page counts (1-page and 2-page fixtures), and
  per-page extracted text (`PDFTextStripper` with start/end page).

**Phase C DONE (2026-07-05): persistence + editor API** — built, unit-tested
(suite 26/26 green), and **verified live e2e** (booted on 8091, Postgres +
Ollama, ~65s pipeline on the small sample): the run persisted the design
(jsonb, revision 1) and its flags; then over the real API: `GET
/api/designs/{jobId}` loaded it; `PUT` with a stale revision → 409 with a
reload message; `PUT` at the right revision → 200, revision 2, and the edited
issue title survived reload; `export?format=pptx|pdf` → real files **containing
the human-edited text** (export renders the *saved* model — Phase C's whole
point); unknown format → 400, unknown job/flag → 404. Gate: inserted a HIGH
unresolved flag → export 409 + `flags` endpoint shows `exportBlocked: true`;
`POST .../flags/1/resolve` with a note → export 200 again.

**Phase C facts:**
- Package `persistence/`: `DesignRecord` (table `design_documents`: `job_id`
  PK, `revision`, `document` **jsonb**, timestamps), `ValidationFlagRecord`
  (table `validation_flags`: one row per fact-check finding + `resolved`/
  `resolution_note`/`resolved_at`), their Spring Data repos, `DesignStore`,
  `FlagStore`, and 404/409 exceptions (`@ResponseStatus`-annotated, so
  controllers just throw).
- **jsonb without Hibernate's JSON machinery** (deliberate — Jackson 3 vs
  Hibernate format-mapper uncertainty): the entity field is a plain JSON
  `String` with `@Column(columnDefinition = "jsonb")` +
  `@ColumnTransformer(write = "?::jsonb")` for inserts; `DesignStore`
  serializes with the **injected Spring `tools.jackson.databind.ObjectMapper`**
  (the same one MVC uses), so stored JSON ≡ wire JSON. Reads come back as
  String and deserialize in the store.
- **Optimistic locking is one atomic native UPDATE**, not read-modify-write:
  `updateIfRevisionMatches` does `set document = cast(? as jsonb), revision =
  expected + 1 where job_id = ? and revision = expected`; 0 rows → existsById
  distinguishes `DesignNotFoundException` (404) from `StaleRevisionException`
  (409). `saveEdit` returns the bumped document so the editor keeps saving
  without reloads. The pipeline's first write (`saveNew`) is a plain insert at
  the document's own revision (1).
- **Export gate moved to the API and is now live-state**: `FlagStore
  .exportBlocked(jobId)` counts unresolved flags with severity in the blocking
  set (computed once from `app.validation.blocking-severity` via the existing
  `meetsOrExceeds` — same config the validation agent uses). Resolving the
  last blocking flag is what unblocks export; the `ValidationReport`'s
  `exportBlocked` remains a point-in-time snapshot for the run response.
- `web/DesignApiController` (`/api/designs`): `GET /{jobId}`, `PUT /{jobId}`
  (body = full `DesignDocument`, its `revision` = the one loaded),
  `GET|POST /{jobId}/export?format=` (GET too so a plain download link works),
  `GET /{jobId}/flags` → `{exportBlocked, flags[]}`,
  `POST /{jobId}/flags/{flagId}/resolve` (optional `{"note": …}`; a flag id
  under the wrong job is 404, not someone else's waiver). Asset serve/upload
  endpoints deferred until Agent #8 actually produces assets.
- `ExportFormat` now carries `mediaType()`/`fileExtension()`/lenient
  `fromName()` — media-type switches deleted from controllers.
- `PipelineService` persists after `orchestrator.run()` returns (flags, then
  design) in short per-store transactions — deliberately *no* pipeline-long
  transaction. `PipelineViewController` lost the throwaway in-memory map and
  its `/design/{jobId}/export.*` endpoint; `result.html` download links go
  through the real API (so dev-UI downloads now honor the gate).
- Tests: `DesignStoreTest` (4 — revision bump lands in returned *and* stored
  JSON, stale → 409-exception, missing → 404-exception, saveNew/load
  round-trip) and `FlagStoreTest` (4 — blocking-set semantics for high/medium
  thresholds, cross-job resolve is NotFound, resolve stamps note). Mockito is
  on the test classpath via the Boot 4 test starters.
- **⚠ DB_URL gotcha (this machine):** the user environment exports
  `DB_URL=jdbc:postgresql://localhost:5432/login` — another project's
  database. A bare `./mvnw spring-boot:run` inherits it and Hibernate will
  happily create our tables there (happened once this session; the two empty
  tables were dropped from `login` again). **Always boot with an explicit
  `DB_URL=jdbc:postgresql://localhost:5432/contentgenerator`** (or unset the
  user-level env var). The `contentgenerator` DB now holds
  `design_documents` + `validation_flags`.

**Phase D — Agent #8 (Image & Graphics) DONE (2026-07-05): built, unit-tested
(4 tests, all green), and verified live e2e** (booted on 8091, Postgres +
Ollama): `POST /api/ingest` on the small sample docx (no embedded images)
returned `graphics: {articlesConsidered: 2, imagesPlaced: 1, placements:
[{section: "Delivery Highlights", article: "...", source: "BRAND_ASSET"}]}` —
correctly fell back to the brand asset since the source doc had none;
`design.componentCount` went from 12 to 13 (exactly one new `ImageBox`).
Confirmed via the real API: `GET /api/designs/{jobId}` showed the new `Asset`
(`storedRef: "assets/GENERIC/team-photo.png"`, dimensions correctly decoded:
320×200) and the `ImageBox` referencing it by `assetId`; the exported PPTX
(`GET .../export?format=pptx`) contained a real `ppt/media/image1.png` (664
bytes, matching the source file — not a placeholder); the web UI's `POST /run`
rendered the Agent #8 stage and the iframe preview showed the image as an
embedded base64 data URI.

**Agent #8 facts:**
- **No `ImageBox` ever existed in the pipeline before this agent** — `LayoutEngine`
  only ever created `TextBox`/`ShapeBox`, and `DesignDocument.assets()` was
  always `List.of()`. Rather than changing `LayoutEngine`/`SectionComposition`
  (Agent #7, already done and unit-tested) to reserve slots, Agent #8 both
  *decides* where an image fits and *fills* it, in one pass over the already-laid-out
  `DesignDocument` — a deliberate scope choice, confirmed with the user before
  building.
- Package `agent/graphics/`: `ImageGraphicsAgent` (`@Order(8)`) delegates to
  `ImagePlacer` (one instance per run, package-private, not a Spring bean —
  holds the run's mutable state: used source-image refs, accumulated assets,
  placements, an id sequence). `AssetLibrary` (`@Component`) is the brand-asset
  lookup. `GraphicsReport`/`ImagePlacement`/`ImageSource` are the output
  records, mirroring `ComplianceReport`'s shape.
- **Geometry heuristic, not a `LayoutEngine` change**: for every `ARTICLE_BODY`
  `TextBox` with a `SourceLink`, `ImagePlacer` looks at the vertical gap
  already left between that box's bottom edge and whichever component comes
  next on the page (or the page's bottom margin if it's last) — if that gap is
  ≥ 48pt (after an 8pt buffer top and bottom), it fits an image there sized to
  the source image's real aspect ratio (decoded via `ImageIO`, default 1.5:1
  if decoding fails), width-capped to the article's own column width. Too
  little room → the article is silently left text-only. Same "accepted v1
  trade-off" spirit as `OverflowResolver`'s clamping — no text reflow, no
  `LayoutEngine` change needed.
- **Image selection, cheapest/most-specific first**: (1) a picture from the
  article's own source document — resolved by walking
  `GeneratedNewsletter` → `GeneratedSection`/`GeneratedArticle` matching the
  placed component's `SourceLink` (section title + headline, the only
  provenance a `Component` carries) → `PlannedItem` → `ContentItem.sources()`
  → match `SourceRef.documentName()` against ingested `DocumentModel`s
  (**same document-level match `SourceTextResolver` already uses** — `.docx`
  extraction stamps every block's `location` as the literal string `"Body"`,
  so there is no finer-than-document-level signal available today; accepted
  as a known v1 limitation, confirmed with the user) → that document's
  `ImageBlock`s, first not-yet-used one wins; (2) falls back to
  `AssetLibrary.findFor(section)` — **brand assets are never marked "used"
  and can repeat** across articles in the same section/issue, unlike
  source-document images, which are each used at most once per run so the
  same photo doesn't appear twice.
- **Brand-asset convention (new, previously just a TODO)**: one approved image
  per file under `storage/<app.graphics.brand-assets-root (default "assets")>/<NewsletterSection
  name>/`, with a `GENERIC/` catch-all folder as the fallback-of-the-fallback —
  editors add files, no config or code change needed. Required adding
  `List<String> list(String relativeDir)` to `StorageService`
  (`LocalFileStorageService` lists regular files under the resolved dir,
  sorted, empty list if the dir doesn't exist — not an error) — the two
  existing hand-rolled `StorageService` test fakes
  (`PptxDesignRendererTest`, `PdfDesignRendererTest`) needed the new method
  added (throwing, since neither fixture exercises it).
- **`HtmlDesignRenderer` fixed in the same pass** (previously always drew the
  dashed placeholder regardless of `assetId`, unlike the PPTX renderer):
  now resolves `ImageBox.assetId()` through `document.assets()` +
  `StorageService`, base64-encodes the bytes into a `data:` URI `<img>` tag
  (a network/endpoint-free choice — no asset-serving endpoint exists yet), and
  falls back to the placeholder on any retrieval failure. Since
  `PdfDesignRenderer` feeds this same HTML through openhtmltopdf verbatim,
  **the PDF export got real images too, at no extra cost** (data URIs need no
  resource loader) — confirmed as a side effect, not separately tested yet.
  `HtmlDesignRenderer` gained a `StorageService` constructor dependency;
  `PdfDesignRendererTest`'s bare `new HtmlDesignRenderer()` needed a
  throwing-fake `StorageService` added.
- `PipelineContext` carries `GraphicsReport` (get/setGraphicsReport) and the
  agent **replaces** the `DesignDocument` on the context with the
  image-enriched one (same "report is the record of what changed" pattern as
  Brand Compliance). `IngestionResponse` gained a `graphics` field
  (`GraphicsSummary` → `PlacementSummary`); `result.html` renders the Agent #8
  stage (stats line + placements table, "SOURCE_DOCUMENT"/"BRAND_ASSET" tag —
  no new CSS class needed, reused `.cat`).
- Asset serve/upload endpoints for the editor are **still open** — this agent
  only needed `StorageService.retrieve` internally (for `ImageIO` dimension
  decoding and, via the renderers, actual bytes), not a public HTTP endpoint.

**Next up → Agent #9 (review).** Also still open: Agents #1–#5 unit tests
(only #6, #7, #8, the renderers, and the stores have them); the design API's
asset serve/upload endpoints (still nothing needs them yet — the editor does,
once it exists). LLM-output lessons carry forward unchanged (lists → bare
arrays, long prose → plain-text protocol, null-guard optional fields) —
Phases C and D make no LLM calls.

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
- [x] Add openhtmltopdf for the PDF renderer →
      `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.40` (maintained fork,
      PDFBox 3.x line). ~~Templating engine for HTML newsletter
      layout~~ — obsolete: the newsletter is a Design Model, not a template
      render; Thymeleaf (already added) serves the dev web UI only.
- [ ] Externalize secrets/config: DB creds (and Ollama URL) via env vars in
      `application.yaml` — no API key needed for local Ollama.
- [x] Configure PostgreSQL datasource + JPA (ddl-auto, dialect) in `application.yaml`
      — exercised for real since Phase C (`design_documents` + `validation_flags`).
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
**Package `agent/validation/`:** `FactValidationAgent` (`@Order(5)`)1 — per
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
- [x] Flag resolution/waiver endpoint — `POST /api/designs/{jobId}/flags/{flagId}/resolve`
      (Phase C); the export gate reads live resolved state from `validation_flags`.
- [ ] Automated unit tests (currently verified manually only).

### 3.6 Brand Compliance Agent  — DONE (live e2e verified + unit tests)
**Package `agent/compliance/`:** `BrandComplianceAgent` (`@Order(6)`) — checks
every generated article against `ComplianceRules` (bound from
`app.compliance.rules` in `application.yaml`): terminology and name-casing
breaches are fixed deterministically (case-preserving substitution, no LLM);
banned buzzwords trigger one LLM rewrite per affected article, accepted only if
the wording is gone and no figures were invented, else reported unfixed for an
editor. Corrected articles replace the `GeneratedNewsletter` on the context.

- [x] Apply writing guidelines + approved terminology (rules config file to start).
- [ ] Enforce formatting standards, approved colors/fonts/logos (visual rules —
      belongs with the Layout & Design agent once there is layout to check).
- [x] Report violations + auto-fix where safe (deterministic first, LLM rewrite
      only for banned phrases, unsafe rewrites rejected).
- [x] Unit tests (`BrandComplianceAgentTest` — deterministic fixes + rewrite
      acceptance rules, LLM stubbed).
- [x] Live end-to-end run through the Spring app — clean run + injected-rule
      run (violations caught, auto-fixed, rendered in the web UI).

### Design Model architecture (decided 2026-07-04 — governs §3.7–§3.10)

**Goal:** an editable newsletter. The pipeline emits a **Design Model** (JSON
design tree), a future Angular editor modifies it, renderer classes export it.
Not an email-HTML generator.

**Core decisions:**
- **Absolute, page-based geometry.** Fixed-size pages with positioned frames
  (x, y, w, h) — like PowerPoint slides. Flow layout would make drag/resize
  editing and PPTX fidelity miserable. Unit = **points** (1/72"; PDF-native,
  ×12,700 = PPTX EMU, maps to CSS px). The template defines the page size
  (A4 portrait to start); PPTX slide size = page size, so one geometry drives
  all renderers with exact fidelity — no per-format re-layout in v1.
- **The LLM never produces geometry.** Semantics (pattern choice, pull-quotes)
  may come from a model; every coordinate comes from deterministic Java.
- **Shared model in `design/`** (peer of `document/`), sealed records,
  Jackson-serializable — the JSON *is* the editor's API payload (one schema, no
  drift): `DesignDocument` (schemaVersion, **revision** for optimistic locking,
  meta, theme, assets, pages) → `Page` → `Component` (sealed: `TextBox` |
  `ImageBox` | `ShapeBox`), each with id, **semantic `role`** (issueTitle,
  sectionTitle, articleBody, sectionIcon, divider…), `frame`, z, locked, and
  **`source` provenance** back to the article/content item (the #2–#5
  fact-chain reaches into the final artifact). `Theme` = pageSize + named
  color roles + named text styles + spacing scale; components reference styles
  (`styleRef: "Body"`) with inline overrides, so theme-level restyling works.
- **Templates are JSON resources** (`resources/design-templates/td-classic.json`
  — theme + section patterns + slots), loaded by a `TemplateCatalog`; editable
  without recompiling, later user-uploadable. Start with one template.
- **Renderers in `render/`** behind a `DesignRenderer` interface +
  `RendererRegistry` (same pattern as `ExtractorRegistry`). The Angular editor
  is effectively a fourth renderer — it draws the JSON itself.
- **Known v1 trade-off:** fixed geometry means no automatic content-aware
  re-layout after human edits (delete an article → boxes don't reflow). The
  pipeline lays out once; afterwards humans move boxes, like PowerPoint.

**Build order:** Phase A = `design/` model + Agent #7 + HTML renderer (proves
the model visually). Phase B = PPTX renderer (POI already present), then PDF.
Phase C = persistence + design CRUD/export API (the editor's contract).
Phase D = Agent #8 (graphics), Agent #9 (review).

### 3.7 Design Composition Agent (+ Layout Engine + Design Model)  — DONE (live e2e verified + unit tests)
**Package `agent/design/`** (`DesignCompositionAgent` `@Order(7)`,
`TemplateCatalog`, `DesignTemplate`, `SectionPattern`, `IconMatcher`,
`CompositionPlan`; deterministic `layout/` engine: `LayoutEngine`,
`TextMeasurer`, `Paginator`, `OverflowResolver`) + shared **`design/`** model.
Two halves: *composition* (semantic, LLM-optional — select template, assign
each section a design pattern the template offers [hero lead story, two-column
section, stat-callout strip, event list], match icons deterministically by
category, optionally lift a key metric into a stat callout) and *layout*
(geometric, 100% deterministic — measure text via AWT font metrics [estimate is
fine, text wraps natively in every renderer], position frames from theme
margins/gutters, paginate, resolve overflow) → `DesignDocument` on the context.

- [x] Shared `design/` model: `DesignDocument`, `Page`, sealed `Component`
      (`TextBox` | `ImageBox` | `ShapeBox`), `Frame`, `Theme`, `TextStyle`,
      `Asset`, `SourceLink` — Jackson round-trip (serialize → deserialize →
      equal) tested.
- [x] `td-classic.json` template (A4 portrait theme + patterns) + `TemplateCatalog`.
- [x] Composition: template selection, pattern per section, icon matching,
      `CompositionPlan` (ordered, no coordinates).
- [x] Layout Engine: text measurement, frame positioning, pagination, overflow
      handling → positioned `DesignDocument`.
- [x] HTML renderer (see §3.10) wired into the result page so the design is
      visible end-to-end (Phase A exit criterion).
- [x] Unit tests: layout invariants (no overlaps, frames inside margins, all
      articles placed) on a fixture newsletter — deterministic, no LLM needed.

### 3.8 Image & Graphics Agent — DONE (live e2e verified + unit tests)
**Package `agent/graphics/`** (`ImageGraphicsAgent` `@Order(8)`, `ImagePlacer`,
`AssetLibrary`, `GraphicsReport`/`ImagePlacement`/`ImageSource`). Enriches the
`DesignDocument`: since no `ImageBox` slots existed anywhere in the pipeline
before this agent, it both *decides* where an image fits (geometry heuristic
over already-laid-out pages, no `LayoutEngine` change) and *fills* it from two
sources — pictures extracted from source documents at ingestion
(`ImageBlock.storedRef`, matched at document level via the same provenance
`SourceTextResolver` uses) and an approved brand-asset directory; registers
`Asset` entries sized from the real decoded image dimensions.

- [x] Approved-asset directory convention (`storage/assets/<NewsletterSection>/`,
      `GENERIC/` catch-all, behind `StorageService`) + `AssetLibrary` lookup
      (required adding `StorageService.list(dir)`).
- [x] Select images for image slots (source-doc images matched via
      document-level provenance first, brand assets as fallback, each
      source-doc image used at most once per run). **Decided with the user:**
      when neither is available, the article is left text-only — no
      placeholder `ImageBox` is added (avoids cluttering every article that
      has no image; a human can still add one in the future editor).
- [ ] Resize/optimize (bounded dimensions, reasonable file size) at export —
      not implemented; images embed at their original size. Revisit if
      real source documents bring in large photos (today's fixtures/live
      test image is tiny).
- [ ] (Dropped from scope: AI image generation.)

### 3.9 Review Agent
**Package `agent/review/`** (`ReviewAgent` `@Order(9)`, `ReviewReport`,
`LayoutLint`, `EditorialCheck`). Quality pass over the finished
`DesignDocument`; findings point at component ids so the editor can show a
review panel. Auto-fix only trivial mechanical issues; everything else is a
finding.

- [ ] Deterministic layout lint: text overflow, overlapping frames, margin
      violations, low-contrast text-on-fill, orphaned section title at page
      bottom.
- [ ] LLM editorial review over text boxes: grammar/spelling/readability —
      bare-array findings DTO (per the extractJson lesson).
- [ ] Quality score + itemized `ReviewReport` on the context, rendered in the
      dev UI.

### 3.10 Export, Persistence & Editor API (renderers, not an agent)
**Packages `render/` + `web/`.** Mostly *not an agent* — infrastructure that
runs on demand, **after** human editing. Export always renders the saved,
possibly human-edited model: the pipeline's output is a draft, not the
deliverable.

- [x] `DesignRenderer` interface + `RendererRegistry` (+ `ExportFormat`).
- [x] **HTML renderer** (`render/html/`): fixed-size page divs, absolutely
      positioned children, theme → inline CSS. Faithful print/preview pages —
      deliberately *not* responsive email HTML. (Built in Phase A with §3.7.)
- [x] **PPTX renderer** (`render/pptx/`): POI XSLF — page → slide (custom slide
      size = page size; POI's shape anchors are already in points, no manual
      EMU math needed), TextBox → text shape, ShapeBox → auto shape,
      ImageBox → picture (or a dashed placeholder until Agent #8 fills
      assets). Output natively editable in PowerPoint. Unit-tested
      (`PptxDesignRendererTest`) and verified live e2e via a "Download PPTX"
      link in the dev UI (`GET /design/{jobId}/export.pptx`, backed by an
      in-memory jobId→DesignDocument cache — real persistence is still below).
- [x] **PDF renderer** (`render/pdf/`): v1 = HTML renderer output →
      openhtmltopdf (`io.github.openhtmltopdf` fork, PDFBox 3.x line;
      absolute-positioned XHTML converts reliably — print CSS pins the
      `@page` box to the theme page size and turns each `div.page` into one
      full-bleed PDF page). Direct PDFBox drawing stays the v2 option if
      fidelity demands it. Unit-tested (`PdfDesignRendererTest` parses the
      bytes back with PDFBox) and verified live via the "Download PDF" link
      (`GET /design/{jobId}/export.pdf` — export endpoint generalized to
      `export.{extension}`).
- [x] Persist `DesignDocument` as Postgres `jsonb` (+ jobId, revision,
      timestamps) — `persistence/DesignRecord` + `DesignStore` (Phase C).
- [x] Editor REST API: `GET /api/designs/{id}` (load), `PUT /api/designs/{id}`
      (save; revision check → 409 on conflict, atomic guarded UPDATE), `GET|POST
      /api/designs/{id}/export?format=pptx|pdf|html` (download). Asset
      serve/upload endpoints deferred to Agent #8 (nothing produces assets yet).
- [x] Move the fact-validation **export gate** here: export answers 409 while
      unresolved flags at blocking severity exist; unblocked live by the
      flag-resolution endpoint (Phase C).
- [ ] (Deferred: Word `.docx` export; email packaging/distribution.)

## Phase 4 — API & Angular Editor

- [ ] REST endpoints: create job, upload docs, trigger pipeline, poll status,
      fetch/download result, resolve validation flags.
- [ ] Design CRUD + export API (defined in §3.10 — load/save Design Model,
      optimistic locking via `revision`, export endpoint).
- [ ] DTOs + validation on all inputs.
- [ ] Progress streaming (SSE/WebSocket).
- [ ] **Angular visual editor** (separate front-end app; Canva /
      PowerPoint-Online-style): loads the Design Model JSON, renders it itself
      (it is effectively a fourth renderer), lets users edit text, move/resize
      components, replace icons/images, then saves the model back and triggers
      export. The existing Thymeleaf pages remain as the dev harness.

## Phase 5 — Quality, Ops, Hardening

- [ ] Unit + integration tests per agent; end-to-end pipeline test with fixtures.
- [ ] Prompt regression tests (golden outputs) for LLM agents.
- [ ] Observability: structured logging, per-agent timing, token/cost tracking.
- [ ] Rate limiting / retry / backoff for LLM calls.
- [ ] Security review: file-upload validation, auth on endpoints, PII handling.
- [ ] Config profiles (dev/prod), secrets management.

---

## Future Enhancements (from spec — not scheduled yet)

- Content-aware re-layout in the editor (delete an article → page reflows);
  v1 lays out once, then humans move boxes like PowerPoint.
- Multiple / user-uploadable design templates (v1 ships `td-classic` only).
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
- [x] Which export format is the real deliverable? → **An editable Design
      Model** is the deliverable; PPTX / PDF / HTML are all first-class renderer
      outputs of it (Word export deferred). Decided 2026-07-04.
- [ ] Human-in-the-loop: is review/approval mandatory before publish, or fully auto?
- [ ] Volume/scale: how many source docs per issue, how large?
