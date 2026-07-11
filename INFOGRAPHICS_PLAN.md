# Infographics Plan — Content-Aware Design Selection

> Companion to `ARCHITECTURE.md` (target-state platform) and `TASKS.md` (build
> status). This document covers one capability: **every newsletter, whatever
> the source content, comes out with proper infographics where the content
> deserves one** — chosen the way a human designer would choose, not randomly
> slapped on. Rendered output stays the existing `DesignDocument` → HTML
> (iframe preview) / PPTX / PDF path.

---

## 0. Goal & guiding rules

**Goal:** for any input documents, the pipeline should produce a newsletter
where enumerable content (steps, pillars, KPIs, phases, categories…) is
rendered as a branded infographic, and non-enumerable content renders exactly
as it does today. "If needed" is the key phrase — an infographic is earned by
the content's shape, never forced.

Locked rules carried over from the existing architecture (do not break):

1. **The LLM never produces geometry.** Design selection is deterministic
   Java over declarative JSON specs. The LLM's only new job is emitting
   *structured points* (text), same as it already emits headlines/bodies.
2. **Additive only.** No change to `Agent`, `AgentOrchestrator`,
   `PipelineContext` fields, or any agent's `execute()` signature. New
   behavior = new `SectionPattern` value + new JSON spec files + new painter
   class + new selection logic inside `DesignCompositionAgent`.
3. **Graceful degradation everywhere.** No points extracted → section renders
   with today's patterns. Text doesn't fit a design → next candidate design
   → ultimately `STANDARD`. A missing/broken spec file → logged and skipped.
   An infographic can never fail a run.

---

## 1. The design library: ~20 designs, 7 archetypes

The reference designs (TCS Talent Development PPT slides) reduce to seven
layout archetypes. Selection reasons about archetypes; the concrete design
within an archetype is a styled variation.

| # | Archetype | Reference designs | Content shape it fits |
|---|-----------|-------------------|-----------------------|
| 1 | `NUMBERED_LIST` | orange 01–04 rounded bars; tag/label rows | 3–5 **sequential or ranked** points, medium text each |
| 2 | `CARD_GRID` | 4 photo cards; 6 gold pods; 6 arch cards; 3 blob cards | 3–6 **parallel, equal-weight** categories, short text |
| 3 | `CYCLE` | 4-segment donut; 5-slice semicircle fan; rainbow arc | points forming a **loop / facets of one theme** |
| 4 | `TIMELINE` | S-curve (4 milestones); snake arrows (8); capsule zigzag (5); winding roadmap | **steps, phases, journey, chronology** |
| 5 | `HUB_SPOKE` | central circle + 6 satellites; hexagon bowtie (3+4) | **one central concept** + supporting points |
| 6 | `KPI_BARS` | chevron bars with 10%/13%/18% + numbered legend | every point carries a **number/percentage** |
| 7 | `SPLIT_VISUAL` | left prose + lightbulb/funnel/arch cards right | 1 article with prose **plus** 3–4 sub-points |

Notes:

- The screenshots are 16:9 slides; the newsletter is A4 portrait. Specs
  define **proportional slot geometry** so a design compresses into a
  half-page or full-page A4 band — never assume slide aspect.
- `KPI_TILES` / `STAT_CALLOUT` already exist as patterns; `KPI_BARS` is the
  richer infographic sibling and reuses the same stat-extraction code.

---

## 2. Piece 1 — Infographic spec catalog (data, not code)

New resource folder `src/main/resources/infographics/*.json`, sibling of
`design-templates/*.json`, loaded by a new `InfographicCatalog` (same shape
as `TemplateCatalog`: classpath JSON → records, cached, name-keyed).

One spec per concrete design. Proposed shape (`InfographicSpec` record):

```jsonc
{
  "name": "numbered-bars-4",
  "archetype": "NUMBERED_LIST",
  "minItems": 3,
  "maxItems": 5,
  "band": "HALF_PAGE",              // HALF_PAGE | FULL_PAGE — A4 space it needs
  "titleCapacity": 40,               // max chars per point label
  "bodyCapacity": 140,               // max chars per point one-liner
  "wantsIcons": true,                // per-point icon slot exists
  "wantsNumbers": false,             // true = every point must carry a numeric value (KPI designs)
  "background": "ANY",              // LIGHT | DARK | ANY — page background suitability
  "slots": [                         // per-point text anchors, fractions of the band frame
    { "titleBox": {"x":0.42,"y":0.05,"w":0.55,"h":0.08},
      "bodyBox":  {"x":0.42,"y":0.13,"w":0.55,"h":0.10},
      "iconBox":  {"x":0.33,"y":0.05,"w":0.07,"h":0.07} }
    // ... one entry per supported item index; specs may define slot sets
    // per item-count (slots3 / slots4 / slots5) where geometry differs
  ],
  "shape": {                         // parameters InfographicPainter needs
    "kind": "numberedBars",
    "colorRoles": ["accent1","accent2","accent3","accent4"]  // theme roles, cycled
  }
}
```

Key decisions:

- **Slots are fractions**, resolved against whatever frame `LayoutEngine`
  reserves — the same design works in a half-page band today and a full page
  later.
- **Colors are theme roles**, not hex — the same infographic recolors itself
  correctly under `td-classic`, `tcs-brand`, `noir-luxe`, `nocturnal-corporate`
  and any future learned template. Dark-background designs (`background`)
  are filtered per theme exactly like the existing black/white logo pick.
- Capacities are in **characters** as the coarse filter; the fine check is
  real measurement via the existing `TextMeasurer` at layout time (§6).

---

## 3. Piece 2 — Structured points from Agent #4 (Content Generation)

Today `GeneratedArticle` = headline + body + source. Infographics need
points. Two sources, in priority order:

1. **LLM-emitted points (primary).** Extend `GenerationPrompts` so that when
   the section's items are enumerable, the model appends points in the
   existing plain-text protocol (small-model lesson: **no JSON here**):

   ```
   HEADLINE: ...
   BODY: ...
   POINT: <short label> | <one-line description>
   POINT: <short label> | <one-line description>
   ```

   Parsed into an optional `List<GeneratedArticle.Point>` (label, text,
   optional numeric value detected via the existing `NUMBER`/`KPI_VALUE`
   patterns). Zero points is valid and common.

2. **Deterministic fallback.** When the LLM emits none but the source
   `ContentItem` has ≥3 `keyMetrics`, derive points from them (value +
   cleaned label — reuse `extractStats()` logic). This guarantees KPI-shaped
   content can always drive `KPI_BARS`/`KPI_TILES` even on a bad model day.

`FactValidationAgent` treats point text like body text (it already traces via
`source`); `BrandComplianceAgent`'s deterministic fixes run over point labels
too — both are string-level passes, no structural change.

---

## 4. Piece 3 — Selection engine (the "human brain")

Lives in `DesignCompositionAgent.compose()`, purely deterministic, runs
before the existing pattern rules:

```
points = article.points()                    (from §3)
if points.size() < 3 → today's logic unchanged (HERO/TWO_COLUMN/STAT/…)

candidates = catalog.all()
  .filter(spec -> points.size() in [minItems, maxItems])
  .filter(spec -> background compatible with theme (Colors.isDark))
  .filter(spec -> !spec.wantsNumbers || every point has a numeric value)
  .filter(spec -> avg label/body length within capacities)   // coarse char check
  .filter(spec -> archetype ∈ intentMatches(section, points)) // see below
  .minus(designs already used this issue)                     // variety
pick = seededRandom(jobId + section.name()).pick(candidates)  // reproducible
```

**Intent matching** — cheap signals already in the pipeline, no LLM call:

| Signal | Source | Archetypes admitted |
|---|---|---|
| every point numeric | point values | `KPI_BARS` (+ existing `KPI_TILES`) |
| sequence words: *step, phase, stage, roadmap, journey, timeline, Q1–Q4, month names* in labels/headline | regex over points | `TIMELINE`, `NUMBERED_LIST` |
| cycle words: *cycle, continuous, loop, lifecycle* | regex | `CYCLE` |
| central-theme: headline noun repeated across points, or section is a themed spotlight | heuristic | `HUB_SPOKE`, `CYCLE` |
| `ItemType`/`BusinessCategory` (e.g. MILESTONE→timeline, ACHIEVEMENT/METRIC→KPI) | already on `ContentItem` | per mapping table |
| no strong signal | — | `CARD_GRID`, `NUMBERED_LIST` (the safe generalists) |

**Randomness with taste:**

- Seeded by `jobId` + section name → re-rendering the same job is stable
  (matters for the editor/persistence flow), different issues differ.
- **Variety constraint:** a concrete design is used at most once per issue;
  consecutive sections avoid repeating an archetype when alternatives exist.
- Empty candidate set → fall through to today's patterns. Never force it.

This is the "wisely use it" requirement: *filter by fit, then randomize among
survivors* — cool and varied, but never a 5-slot design holding 3 points or a
timeline wrapped around unrelated categories.

---

## 5. Piece 4 — Rendering (hybrid: SVG shapes + real text boxes)

New `SectionPattern.INFOGRAPHIC`, carried on `SectionComposition` together
with the chosen `InfographicSpec` name + resolved points.

**`LayoutEngine`** (stays 100% deterministic):

- Reserves the band frame (half/full page per spec, paginating like any
  section; `keep-with-next` applies to its section header).
- Places one `TextBox` per point per slot (title/body), styles from existing
  `TextStyle` roles (new roles `infographicLabel`, `infographicBody` added to
  the four template JSONs with sensible defaults).
- Emits one `ImageBox` with asset id `decor-infographic-<specName>-<cmpId>`
  covering the band — the same well-known-decor-id contract the renderers
  already resolve, so **HTML/PPTX/PDF renderers need zero changes**.
- Per-point icons: reuse `assets/ICONS/` + `IconMatcher`, extended with a
  keyword→icon match over point labels; no icon found → the painter draws
  the numbered disc instead (every reference design has a numeric variant).

**`InfographicPainter`** (new, sibling of `DecorPainter`):

- `paint(spec, theme, w, h, itemCount)` → SVG string: the bars, arcs,
  chevrons, donut segments, connector lines — *around* the text slots, which
  it leaves empty (text is real text on top).
- Stored via the existing `decorAssets()` flow in `DesignCompositionAgent`
  (job `decor/` folder, attached as `Asset`) — one new `case "infographic"`
  in that switch.

Why hybrid and not one flat SVG with text baked in: text stays selectable,
editable (future Angular editor), theme-styled, measurable for overflow, and
export-faithful in PPTX/PDF where SVG text support is weakest.

---

## 6. Piece 5 — Guardrails (Agent #9 + layout-time checks)

1. **Layout-time fit check (primary):** before committing the infographic,
   `LayoutEngine` measures each point's text (`TextMeasurer`) against its
   slot. Overflow → `DesignCompositionAgent` retries with the next candidate
   from §4's list; list exhausted → `STANDARD`. The char-capacity filter
   makes this rare; the measurement makes it impossible to ship clipped text.
2. **`LayoutLint` check (backstop):** new lint — text box overlapping the
   infographic's shape regions or overflowing its frame → `ReviewFinding`,
   same severity flow as existing lints.
3. **Decor failure isolation:** unknown/failed spec → decor asset simply not
   attached (existing behavior), section still shows its text content.

---

## 7. Build order

| Phase | Deliverable | Proves | Status |
|---|---|---|---|
| **1** | `InfographicSpec` + `InfographicCatalog` + `SectionPattern.INFOGRAPHIC` + `InfographicPainter` + **one archetype end-to-end: `NUMBERED_LIST`** (easiest geometry) | whole chain renders in the iframe HTML + PPTX/PDF | ✅ DONE 2026-07-11 |
| **2** | Agent #4 structured points (`POINT:` protocol) + parser tests. *(keyMetrics fallback dropped by design: purely numeric content already has `KPI_TILES`, which IS the KPI archetype — auto-deriving points from metrics would have silently rerouted it)* | points exist for real content | ✅ DONE 2026-07-11 |
| **3** | Selection engine: filters, intent table, seeded randomness, variety constraint, fallback chain | "wise" selection on varied inputs | ✅ DONE 2026-07-11 |
| **4** | Remaining archetypes by geometry difficulty: `CARD_GRID` → `KPI_BARS` → `TIMELINE` (S-curve, zigzag) → `CYCLE` (donut, fan) → `HUB_SPOKE` → `SPLIT_VISUAL`; 2–3 concrete specs each ≈ 15–20 designs | full library | next |
| **5** | Fit-check retry loop + `LayoutLint` rule + per-point icon matching *(char-capacity filter shipped in Phase 3; measurement retry pending)* | quality floor | partial |

Each phase leaves the pipeline green: until Phase 3 wires selection in, no
production section renders differently.

## 8. Out of scope (deliberately)

- **Learned infographic extraction** (auto-deriving specs from uploaded PPTs)
  — belongs to the Template Learning Pipeline (`ARCHITECTURE.md` §11); this
  plan's JSON spec format is designed to be its future output shape.
- **LLM-chosen designs** — selection stays deterministic; revisit only if
  the intent table proves insufficient on real content.
- **Per-user template selection UX** — tracked in `TASKS.md`.
