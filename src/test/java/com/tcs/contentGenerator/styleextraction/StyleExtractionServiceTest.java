package com.tcs.contentGenerator.styleextraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.design.DesignTemplate;
import com.tcs.contentGenerator.agent.design.TemplateCatalog;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.llm.LlmImage;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.styleextraction.ReferenceRasterizer.PageImage;
import com.tcs.contentGenerator.styleextraction.StyleExtractionService.StyleExtractionResult;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** LLM and rasterizer stubbed — no Ollama, no real PDFs. */
class StyleExtractionServiceTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TemplateCatalog TEMPLATES =
            new TemplateCatalog(JsonMapper.builder().build(), "tcs-brand");

    private static StyleDescription description(String name, String text, String primary) {
        return new StyleDescription(name, "#FFFFFF", "#F2F4F7", text, "#5F6B7A", primary,
                "#FBB034", "#54B948", "#E1E4E8", "Avenir Next", "rounded and friendly",
                "thin line icons", "airy single-column",
                primary, "#FFFFFF", "wave", "ellipse", "yes", "A calm corporate feel.");
    }

    @Test
    void happyPathBuildsAndStoresDraftTemplate() {
        FakeStorage storage = new FakeStorage(List.of("references/ref.pdf", "references/notes.txt"));
        StyleExtractionService service = service(storage,
                Map.of("ref.pdf", List.of(page("p1"), page("p2"))),
                visionNotes(Map.of("p1", "blue and white, rounded", "p2", "yellow accents")),
                description("Ocean Corporate!", "#1A1A1A", "#1A73E8"));

        StyleExtractionResult result = service.extract();

        assertEquals(1, result.referencesAnalyzed(), "the .txt must be skipped");
        assertEquals(2, result.imagesAnalyzed());
        assertEquals(2, result.pageNotes().size());
        assertEquals("ocean-corporate", result.template().name());
        assertEquals("#1A73E8", result.template().theme().colors().get("primary"));
        assertEquals("references/extracted/ocean-corporate.json", result.storedRef());

        // the stored draft round-trips as a loadable DesignTemplate
        DesignTemplate stored = MAPPER.readValue(
                new String(storage.stored.get(result.storedRef()), StandardCharsets.UTF_8),
                DesignTemplate.class);
        assertEquals("ocean-corporate", stored.name());
        assertEquals("#1A73E8", stored.theme().colors().get("primary"));
        // text styles keep the default template's structure/sizes, restyled font only
        var defaults = TEMPLATES.getDefault().theme().textStyles();
        assertEquals(defaults.keySet(), stored.theme().textStyles().keySet());
        assertEquals("Avenir Next", stored.theme().textStyles().get("Body").fontFamily());
        assertEquals(defaults.get("Body").fontSizePt(), stored.theme().textStyles().get("Body").fontSizePt());
    }

    @Test
    void invalidHexFallsBackToDefaultTemplateColor() {
        FakeStorage storage = new FakeStorage(List.of("references/ref.pdf"));
        StyleExtractionService service = service(storage,
                Map.of("ref.pdf", List.of(page("p1"))),
                visionNotes(Map.of("p1", "note")),
                description("x", "#1A1A1A", "a deep blueish tone"));

        StyleExtractionResult result = service.extract();

        String defaultPrimary = TEMPLATES.getDefault().theme().colors().get("primary");
        assertEquals(defaultPrimary, result.template().theme().colors().get("primary"));
    }

    @Test
    void lowContrastTextColorIsReplacedInTheDraft() {
        FakeStorage storage = new FakeStorage(List.of("references/ref.pdf"));
        // TCS Blue on white is ~3.87:1 — the exact trap the issue title already hit
        StyleExtractionService service = service(storage,
                Map.of("ref.pdf", List.of(page("p1"))),
                visionNotes(Map.of("p1", "note")),
                description("x", "#4E84C4", "#4E84C4"));

        StyleExtractionResult result = service.extract();

        assertEquals("#000000", result.template().theme().colors().get("text"));
        assertEquals("#4E84C4", result.template().theme().colors().get("primary"),
                "non-text roles keep the extracted color");
    }

    @Test
    void oneFailedVisionCallLosesOnePageNotTheRun() {
        FakeStorage storage = new FakeStorage(List.of("references/ref.pdf"));
        StyleExtractionService service = service(storage,
                Map.of("ref.pdf", List.of(page("boom"), page("p2"))),
                prompt -> {
                    if (prompt.contains("boom")) {
                        throw new RuntimeException("vision call failed");
                    }
                    return "usable note";
                },
                description("x", "#1A1A1A", "#1A73E8"));

        StyleExtractionResult result = service.extract();

        assertEquals(1, result.pageNotes().size());
        assertNotNull(result.template());
    }

    @Test
    void emptyMergeResultIsRetriedOnce() {
        FakeStorage storage = new FakeStorage(List.of("references/ref.pdf"));
        StyleExtractionService service = service(storage,
                Map.of("ref.pdf", List.of(page("p1"))),
                visionNotes(Map.of("p1", "note")),
                emptyDescription(), description("Second Try", "#1A1A1A", "#1A73E8"));

        StyleExtractionResult result = service.extract();

        assertEquals("second-try", result.template().name());
        assertEquals("#1A73E8", result.template().theme().colors().get("primary"));
    }

    @Test
    void persistentlyEmptyMergeFallsBackToDefaultTemplateValues() {
        FakeStorage storage = new FakeStorage(List.of("references/ref.pdf"));
        StyleExtractionService service = service(storage,
                Map.of("ref.pdf", List.of(page("p1"))),
                visionNotes(Map.of("p1", "note")),
                emptyDescription(), emptyDescription());

        StyleExtractionResult result = service.extract();

        assertEquals("extracted-style", result.template().name());
        assertEquals(TEMPLATES.getDefault().theme().colors(), result.template().theme().colors());
        assertEquals(1, result.pageNotes().size(), "the vision notes must survive for the human");
    }

    @Test
    void decorIsBuiltFromTheExtractedObservations() {
        FakeStorage storage = new FakeStorage(List.of("references/ref.pdf"));
        // masthead #1A73E8 → nearest role is primary (#1A73E8 exactly); ellipse + shadows
        StyleExtractionService service = service(storage,
                Map.of("ref.pdf", List.of(page("p1"))),
                visionNotes(Map.of("p1", "note")),
                description("x", "#1A1A1A", "#1A73E8"));

        var template = service.extract().template();

        assertNotNull(template.decor());
        assertEquals("primary", template.decor().masthead().from());
        assertEquals("background", template.decor().masthead().to(), "white masthead-to snaps to background");
        assertEquals("wave", template.decor().masthead().edge());
        assertEquals("ellipse", template.decor().photo().clip());
        assertTrue(template.decor().photo().shadow());
        // the on-band style must exist and contrast with the band's MIDDLE and END
        // (the title sits right of the logo, past the gradient's start): the band
        // runs #1A73E8 -> white, so near-black text wins over white text
        assertNotNull(template.theme().textStyles().get("IssueTitleOnBand"));
        assertEquals("text", template.theme().textStyles().get("IssueTitleOnBand").colorRole());
    }

    @Test
    void invalidDecorObservationsFallBackToSafeDefaults() {
        FakeStorage storage = new FakeStorage(List.of("references/ref.pdf"));
        StyleDescription base = description("x", "#1A1A1A", "#1A73E8");
        StyleDescription vague = new StyleDescription(base.templateName(), base.background(),
                base.surface(), base.text(), base.muted(), base.primary(), base.secondary(),
                base.accent(), base.divider(), base.fontFamily(), base.typographyMood(),
                base.iconographyStyle(), base.layoutMood(),
                "a blueish tone", null, "curvy?", "freeform", "maybe", base.summary());
        StyleExtractionService service = service(storage,
                Map.of("ref.pdf", List.of(page("p1"))),
                visionNotes(Map.of("p1", "note")), vague);

        var decor = service.extract().template().decor();

        assertEquals("primary", decor.masthead().from(), "invalid hex falls back to primary");
        assertEquals("background", decor.masthead().to());
        assertEquals("flat", decor.masthead().edge());
        assertEquals("none", decor.photo().clip());
        assertTrue(!decor.photo().shadow());
    }

    @Test
    void noReferencePdfsIsAnError() {
        FakeStorage storage = new FakeStorage(List.of("references/readme.txt"));
        StyleExtractionService service = service(storage, Map.of(),
                visionNotes(Map.of()), description("x", "#1A1A1A", "#1A73E8"));

        IllegalStateException e = assertThrows(IllegalStateException.class, service::extract);
        assertTrue(e.getMessage().contains("No reference PDFs"));
    }

    // ---- fixture plumbing ----------------------------------------------------

    private interface VisionAnswer {
        String answer(String userPrompt);
    }

    private static VisionAnswer visionNotes(Map<String, String> noteByLabel) {
        return prompt -> noteByLabel.entrySet().stream()
                .filter(e -> prompt.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("generic note");
    }

    private static StyleExtractionService service(FakeStorage storage,
            Map<String, List<PageImage>> imagesByFileName, VisionAnswer vision,
            StyleDescription... mergeResults) {
        ReferenceRasterizer rasterizer = new ReferenceRasterizer(1024, 3) {
            @Override
            public List<PageImage> rasterize(Path pdf) {
                return imagesByFileName.getOrDefault(pdf.getFileName().toString(), List.of());
            }
        };
        LlmClient llm = new LlmClient() {
            private int mergeCalls;

            @Override
            public String generate(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException("not used by style extraction");
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, List<LlmImage> images) {
                return vision.answer(userPrompt);
            }

            @Override
            public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
                // successive merge calls walk the given results; the last one repeats
                StyleDescription result = mergeResults[Math.min(mergeCalls++, mergeResults.length - 1)];
                return responseType.cast(result);
            }
        };
        return new StyleExtractionService(storage, rasterizer, llm, TEMPLATES, MAPPER,
                "references", "references/extracted");
    }

    /** What a schema echo parses into: a description with every field null. */
    private static StyleDescription emptyDescription() {
        return StyleDescription.empty();
    }

    /** Labels double as page content markers so the vision stub can key off the prompt. */
    private static PageImage page(String label) {
        return new PageImage(label, new byte[] {1});
    }

    private static final class FakeStorage implements StorageService {
        private final List<String> refs;
        final Map<String, byte[]> stored = new HashMap<>();

        FakeStorage(List<String> refs) {
            this.refs = new ArrayList<>(refs);
        }

        @Override
        public String store(String relativePath, byte[] content) {
            stored.put(relativePath, content);
            return relativePath;
        }

        @Override
        public byte[] retrieve(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path resolve(String ref) {
            // keep the file name intact so the fake rasterizer can key off it
            return Path.of(ref);
        }

        @Override
        public void delete(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> list(String relativeDir) {
            return refs;
        }
    }
}
