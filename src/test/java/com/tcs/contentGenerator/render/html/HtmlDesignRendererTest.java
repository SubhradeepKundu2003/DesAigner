package com.tcs.contentGenerator.render.html;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.render.font.BrandFontRegistry;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Covers the two things {@link BrandFontRegistry} wiring added to this
 * renderer: the {@code @font-face} rule is emitted only when a brand font is
 * actually available, and multi-word font-family names come out quoted (an
 * existing bug this change surfaced — an unquoted "Houschka Rounded,
 * sans-serif" is ambiguous/invalid CSS).
 */
class HtmlDesignRendererTest {

    private static final Theme THEME = new Theme(
            new PageSize(595, 842),
            Map.of("background", "#FFFFFF", "text", "#000000"),
            Map.of("Body", new TextStyle("Houschka Rounded", 10, "normal", "text", 14)),
            new Spacing(48, 16));

    private static DesignDocument documentWithOneTextBox() {
        TextBox body = new TextBox("cmp-1", ComponentRole.ARTICLE_BODY,
                new Frame(48, 48, 300, 20), 0, false, null, "Body", "Hello");
        return new DesignDocument(1, 1, new DesignMeta("Issue", "job-1"), THEME, List.of(),
                List.of(new Page("page-1", List.of(body))));
    }

    /**
     * Real font files now live on the classpath, so the "no brand font" state
     * has to be simulated — the fallback path must keep working for any
     * template whose font is not bundled.
     */
    private static BrandFontRegistry emptyRegistry() {
        return new BrandFontRegistry() {
            @Override
            public Optional<byte[]> bytesFor(String weight) {
                return Optional.empty();
            }
        };
    }

    @Test
    void omitsFontFaceRuleWhenBrandFontFileIsAbsent() {
        HtmlDesignRenderer renderer = new HtmlDesignRenderer(new NoopStorageService(), emptyRegistry());

        String html = renderer.renderHtml(documentWithOneTextBox(), "");

        assertFalse(html.contains("@font-face"), "expected no @font-face rule when no brand font is bundled");
    }

    @Test
    void emitsFontFaceRulesFromTheRealBundledFonts() {
        HtmlDesignRenderer renderer = new HtmlDesignRenderer(new NoopStorageService(), new BrandFontRegistry());

        String html = renderer.renderHtml(documentWithOneTextBox(), "");

        assertTrue(html.contains("@font-face{font-family:\"Houschka Rounded\";font-weight:normal"),
                "expected a normal-weight @font-face rule from the bundled fonts");
        assertTrue(html.contains("@font-face{font-family:\"Houschka Rounded\";font-weight:bold"),
                "expected a bold-weight @font-face rule from the bundled fonts");
    }

    @Test
    void emitsFontFaceRuleWhenBrandFontFileIsPresent() {
        byte[] fakeBytes = "not-a-real-font-just-test-bytes".getBytes(StandardCharsets.UTF_8);
        BrandFontRegistry registry = new BrandFontRegistry() {
            @Override
            public Optional<byte[]> bytesFor(String weight) {
                return "bold".equalsIgnoreCase(weight) ? Optional.empty() : Optional.of(fakeBytes);
            }
        };
        HtmlDesignRenderer renderer = new HtmlDesignRenderer(new NoopStorageService(), registry);

        String html = renderer.renderHtml(documentWithOneTextBox(), "");

        assertTrue(html.contains("@font-face{font-family:\"Houschka Rounded\";font-weight:normal"),
                "expected a normal-weight @font-face rule: " + html);
        assertTrue(html.contains(Base64.getEncoder().encodeToString(fakeBytes)),
                "expected the font bytes to be base64-embedded: " + html);
        assertFalse(html.contains("font-weight:bold;src"), "no bold file was supplied, so no bold rule expected");
    }

    @Test
    void quotesMultiWordFontFamilyNamesWithSingleQuotes() {
        // Empty registry: the double-quote ban below is about inline style="..."
        // attributes; a real registry legitimately double-quotes the family
        // inside the <style> block's @font-face rule.
        HtmlDesignRenderer renderer = new HtmlDesignRenderer(new NoopStorageService(), emptyRegistry());

        String html = renderer.renderHtml(documentWithOneTextBox(), "");

        // Single-quoted, not double: the value sits inside a double-quoted style="..."
        // attribute, so a double-quoted family name would break the XHTML.
        assertTrue(html.contains("font-family:'Houschka Rounded',sans-serif"),
                "expected the family name to be single-quoted in the generated CSS: " + html);
        assertFalse(html.contains("font-family:\"Houschka Rounded\""),
                "double-quoting the family name here would break the style attribute: " + html);
    }

    @Test
    void embedsSvgAssetsWithTheCorrectMimeType() {
        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        Asset logoAsset = new Asset("brand-logo", "image", "assets/BRAND/logo_black.svg", null, null);
        ImageBox logo = new ImageBox("cmp-1", ComponentRole.LOGO,
                new Frame(48, 48, 40, 40), 0, true, null, "brand-logo", "Company logo");
        DesignDocument document = new DesignDocument(1, 1, new DesignMeta("Issue", "job-1"), THEME,
                List.of(logoAsset), List.of(new Page("page-1", List.of(logo))));
        HtmlDesignRenderer renderer = new HtmlDesignRenderer(
                new FixedStorageService(Map.of("assets/BRAND/logo_black.svg", svgBytes)), new BrandFontRegistry());

        String html = renderer.renderHtml(document, "");

        assertTrue(html.contains("data:image/svg+xml;base64,"),
                "expected the SVG logo to be embedded with an image/svg+xml data URI: " + html);
    }

    /**
     * Real bug this test caught: {@code altText} (free LLM-generated text —
     * article headlines, section titles) lands inside a double-quoted
     * {@code alt="..."} attribute. A literal {@code "} in that text used to
     * flow through un-escaped, breaking openhtmltopdf's strict XML parse and
     * failing PDF export while the lenient browser preview kept working —
     * making the failure look random rather than content-triggered.
     */
    @Test
    void escapesDoubleQuotesInAltTextSoTheAttributeCannotBreakOut() {
        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        Asset asset = new Asset("photo-1", "image", "assets/x.svg", null, null);
        ImageBox photo = new ImageBox("cmp-1", ComponentRole.IMAGE_PLACEHOLDER,
                new Frame(48, 48, 40, 40), 0, false, null, "photo-1", "Team says \"great quarter\"");
        DesignDocument document = new DesignDocument(1, 1, new DesignMeta("Issue", "job-1"), THEME,
                List.of(asset), List.of(new Page("page-1", List.of(photo))));
        HtmlDesignRenderer renderer = new HtmlDesignRenderer(
                new FixedStorageService(Map.of("assets/x.svg", svgBytes)), new BrandFontRegistry());

        String html = renderer.renderHtml(document, "");

        assertTrue(html.contains("alt=\"Team says &quot;great quarter&quot;\""),
                "expected the embedded quotes to be escaped as entities: " + html);
        assertFalse(html.contains("alt=\"Team says \"great quarter\"\""),
                "an un-escaped quote here would prematurely close the attribute: " + html);
    }

    /** No fixture here uses a real image asset, so every method is unreachable. */
    private static final class NoopStorageService implements StorageService {
        @Override
        public String store(String relativePath, byte[] content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] retrieve(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path resolve(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> list(String relativeDir) {
            throw new UnsupportedOperationException();
        }
    }

    private record FixedStorageService(Map<String, byte[]> content) implements StorageService {
        @Override
        public String store(String relativePath, byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] retrieve(String ref) {
            byte[] bytes = content.get(ref);
            if (bytes == null) {
                throw new IllegalStateException("No content for ref " + ref);
            }
            return bytes;
        }

        @Override
        public Path resolve(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> list(String relativeDir) {
            throw new UnsupportedOperationException();
        }
    }
}
