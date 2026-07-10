package com.tcs.contentGenerator.styleextraction;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tcs.contentGenerator.agent.design.Decor;
import com.tcs.contentGenerator.agent.design.DesignTemplate;
import com.tcs.contentGenerator.agent.design.TemplateCatalog;
import com.tcs.contentGenerator.design.Colors;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.llm.LlmClient;
import com.tcs.contentGenerator.llm.LlmImage;
import com.tcs.contentGenerator.storage.StorageService;
import com.tcs.contentGenerator.styleextraction.ReferenceRasterizer.PageImage;

import tools.jackson.databind.ObjectMapper;

/**
 * Reference style extraction (the first sliver of the future Design Learning
 * Pipeline): rasterizes the design references staged under
 * {@code storage/references/}, has the vision model describe each page's
 * <em>style</em> (palette, typography mood, iconography, layout mood — never
 * coordinates; layout stays deterministic Java), merges the notes into one
 * {@link StyleDescription}, and materializes it as a <em>draft</em>
 * {@link DesignTemplate} JSON under {@code storage/references/extracted/}.
 * The draft reuses the default template's page geometry, spacing, and text
 * style structure (sizes are layout-tuned, so only colors and font family are
 * restyled) and is meant for human review — copy it into
 * {@code resources/design-templates/} to activate it.
 */
@Service
public class StyleExtractionService {

    private static final Logger log = LoggerFactory.getLogger(StyleExtractionService.class);

    private static final Pattern HEX_COLOR = Pattern.compile("#?([0-9a-fA-F]{6})");
    private static final List<String> COLOR_ROLES = List.of(
            "background", "surface", "text", "muted", "primary", "secondary", "accent", "divider");
    /** Same threshold Agent #9's contrast lint gates on (app.review default). */
    private static final double MIN_TEXT_CONTRAST = 4.5;

    private final StorageService storage;
    private final ReferenceRasterizer rasterizer;
    private final LlmClient llm;
    private final TemplateCatalog templateCatalog;
    private final ObjectMapper objectMapper;
    private final String referencesRoot;
    private final String outputDir;

    public StyleExtractionService(StorageService storage, ReferenceRasterizer rasterizer,
            LlmClient llm, TemplateCatalog templateCatalog, ObjectMapper objectMapper,
            @Value("${app.style-extraction.references-root:references}") String referencesRoot,
            @Value("${app.style-extraction.output-dir:references/extracted}") String outputDir) {
        this.storage = storage;
        this.rasterizer = rasterizer;
        this.llm = llm;
        this.templateCatalog = templateCatalog;
        this.objectMapper = objectMapper;
        this.referencesRoot = referencesRoot;
        this.outputDir = outputDir;
    }

    /** A single page's style notes, kept in the result so a human can refine the draft. */
    public record PageNote(String label, String note) {
    }

    public record StyleExtractionResult(int referencesAnalyzed, int imagesAnalyzed,
            List<PageNote> pageNotes, StyleDescription description, DesignTemplate template,
            String storedRef) {
    }

    public StyleExtractionResult extract() {
        List<String> pdfRefs = storage.list(referencesRoot).stream()
                .filter(ref -> ref.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                .toList();
        if (pdfRefs.isEmpty()) {
            throw new IllegalStateException(
                    "No reference PDFs found under storage/" + referencesRoot + "/");
        }

        List<PageNote> notes = new ArrayList<>();
        for (String ref : pdfRefs) {
            for (PageImage image : rasterizer.rasterize(storage.resolve(ref))) {
                // Failure isolation per image: one bad vision call loses one page's
                // notes, never the run (same pattern as generation/validation).
                try {
                    String note = llm.generate(StyleExtractionPrompts.SYSTEM,
                            StyleExtractionPrompts.describePage(image.label()),
                            List.of(LlmImage.png(image.png())));
                    notes.add(new PageNote(image.label(), note == null ? "" : note.strip()));
                } catch (RuntimeException e) {
                    log.warn("Vision style call failed for {} — skipping this page", image.label(), e);
                }
            }
        }
        if (notes.isEmpty()) {
            throw new IllegalStateException(
                    "Style extraction produced no page notes (all vision calls failed?)");
        }

        StyleDescription description = mergeNotes(notes);

        DesignTemplate draft = toDraftTemplate(description);
        String storedRef = storage.store(outputDir + "/" + draft.name() + ".json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(draft)
                        .getBytes(StandardCharsets.UTF_8));
        log.info("Style extraction complete: {} reference(s), {} page image(s) → draft template '{}' at {}",
                pdfRefs.size(), notes.size(), draft.name(), storedRef);
        return new StyleExtractionResult(pdfRefs.size(), notes.size(), notes, description, draft, storedRef);
    }

    /**
     * Merges the page notes into one {@link StyleDescription}. Observed live:
     * the small model sometimes answers the structured merge call with the JSON
     * <em>Schema</em> instead of an instance — which parses "successfully" into
     * a record with every field null. Detect that (no color, name, or font
     * survived) and retry once; if the retry is empty too, carry on — the draft
     * then keeps the default template's values and the page notes in the result
     * still give a human everything the vision pass saw.
     */
    private StyleDescription mergeNotes(List<PageNote> notes) {
        List<String> noteTexts = notes.stream().map(PageNote::note).toList();
        StyleDescription description = llm.generate(StyleExtractionPrompts.SYSTEM,
                StyleExtractionPrompts.merge(noteTexts), StyleDescription.class);
        if (isEmpty(description)) {
            log.warn("Style merge returned no usable fields (schema echo?) — retrying once");
            description = llm.generate(StyleExtractionPrompts.SYSTEM,
                    StyleExtractionPrompts.merge(noteTexts), StyleDescription.class);
        }
        if (isEmpty(description)) {
            log.warn("Style merge retry was empty too — the draft will carry the default template's values");
            return StyleDescription.empty();
        }
        return description;
    }

    private static boolean isEmpty(StyleDescription d) {
        if (d == null) {
            return true;
        }
        return COLOR_ROLES.stream().map(role -> extractedColor(d, role)).allMatch(StyleExtractionService::blank)
                && blank(d.templateName()) && blank(d.fontFamily());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Deterministic mapping from the LLM's description to a template: every hex
     * is validated (invalid/missing → the default template's value for that
     * role), and the text role must clear Agent #9's contrast gate against the
     * extracted background — otherwise every issue built from this draft would
     * flag its own body text (the exact trap TCS Blue set for the issue title).
     * The description's masthead/photo/shadow observations become the draft's
     * {@code Decor} spec — extracted masthead hexes are snapped to the nearest
     * theme color role (decor references roles, never literals), and the
     * on-band title style picks whichever of text/background contrasts better
     * with the band's start color.
     */
    private DesignTemplate toDraftTemplate(StyleDescription description) {
        Theme defaults = templateCatalog.getDefault().theme();

        Map<String, String> colors = new LinkedHashMap<>();
        for (String role : COLOR_ROLES) {
            colors.put(role, sanitizeHex(extractedColor(description, role), defaults.colors().get(role)));
        }
        String background = colors.get("background");
        if (contrastRatio(colors.get("text"), background) < MIN_TEXT_CONTRAST) {
            String replacement = contrastRatio("#000000", background)
                    >= contrastRatio("#FFFFFF", background) ? "#000000" : "#FFFFFF";
            log.info("Extracted text color {} fails {}:1 contrast on {} — using {} in the draft",
                    colors.get("text"), MIN_TEXT_CONTRAST, background, replacement);
            colors.put("text", replacement);
        }

        String mastheadFromRole = nearestRole(description.mastheadFrom(), colors, "primary");
        String mastheadToRole = nearestRole(description.mastheadTo(), colors, "background");
        // The title sits right of the logo — over the gradient's middle and end,
        // not its start — so judge candidates by their WORST contrast against the
        // band's midpoint and end colors and take the better one. (Judging only
        // the start color picked a near-invisible title on a gold→dark band.)
        String onBandRole = minContrastOverBand(colors.get("text"), colors, mastheadFromRole, mastheadToRole)
                >= minContrastOverBand(colors.get("background"), colors, mastheadFromRole, mastheadToRole)
                        ? "text" : "background";

        String fontFamily = description.fontFamily() == null ? "" : description.fontFamily().strip();
        Map<String, TextStyle> textStyles = new LinkedHashMap<>();
        defaults.textStyles().forEach((name, style) -> textStyles.put(name, new TextStyle(
                fontFamily.isEmpty() ? style.fontFamily() : fontFamily,
                style.fontSizePt(), style.fontWeight(), style.colorRole(), style.lineHeightPt())));
        TextStyle issueTitle = textStyles.get("IssueTitle");
        if (issueTitle != null) {
            textStyles.put("IssueTitleOnBand", new TextStyle(issueTitle.fontFamily(),
                    issueTitle.fontSizePt(), issueTitle.fontWeight(), onBandRole, issueTitle.lineHeightPt()));
        }
        // editorial styles: a larger muted lead paragraph, and a colored kicker
        // section title — colored only if the brand color clears the contrast
        // gate on the page background (the TCS-Blue-on-white trap, again)
        TextStyle body = textStyles.get("Body");
        if (body != null) {
            textStyles.put("BodyLead", new TextStyle(body.fontFamily(), body.fontSizePt() + 1.5,
                    body.fontWeight(), "muted", body.lineHeightPt() + 2));
        }
        TextStyle sectionTitle = textStyles.get("SectionTitle");
        if (sectionTitle != null) {
            String kickerRole = contrastRatio(colors.get("primary"), background) >= MIN_TEXT_CONTRAST
                    ? "primary" : "text";
            textStyles.put("SectionTitleKicker", new TextStyle(sectionTitle.fontFamily(),
                    sectionTitle.fontSizePt(), sectionTitle.fontWeight(), kickerRole,
                    sectionTitle.lineHeightPt()));
        }

        boolean shadows = "yes".equalsIgnoreCase(strip(description.shadows()));
        String photoClip = switch (strip(description.photoShape()).toLowerCase(Locale.ROOT)) {
            case "ellipse", "oval", "circle", "circular" -> "ellipse";
            case "rounded" -> "rounded";
            default -> "none";
        };
        // cover: always the dark face of the theme, title in whichever base
        // color contrasts best with it, subtitle muted only if it stays legible
        String coverFillRole = Colors.isDark(background) ? "background" : "text";
        String coverFillHex = colors.get(coverFillRole);
        String coverTitleRole = contrastRatio(colors.get("text"), coverFillHex)
                >= contrastRatio(colors.get("background"), coverFillHex) ? "text" : "background";
        String coverSubtitleRole = contrastRatio(colors.get("muted"), coverFillHex) >= MIN_TEXT_CONTRAST
                ? "muted" : coverTitleRole;
        TextStyle coverBase = textStyles.get("IssueTitle");
        if (coverBase != null) {
            textStyles.put("CoverTitle", new TextStyle(coverBase.fontFamily(), 34, "normal",
                    coverTitleRole, 40, "right"));
            textStyles.put("CoverTitleAccent", new TextStyle(coverBase.fontFamily(), 40, "bold",
                    "primary", 46, "right"));
            textStyles.put("CoverSubtitle", new TextStyle(coverBase.fontFamily(), 11, "normal",
                    coverSubtitleRole, 15, "right"));
        }

        Decor decor = new Decor(
                new Decor.Cover(coverFillRole, "primary"),
                new Decor.Masthead("gradient-band", mastheadFromRole, mastheadToRole, 0, 130,
                        "wave".equalsIgnoreCase(strip(description.mastheadEdge())) ? "wave" : "flat"),
                new Decor.SectionHeader("chip", "primary", true),
                // the reference material is image-led magazine design — hero
                // gets the full-width photo treatment
                new Decor.Hero("photo-led", "surface", "primary"),
                new Decor.SectionBand("surface"),
                new Decor.Photo(photoClip, 12, shadows, "side"),
                new Decor.StatCard("surface", "primary", shadows),
                new Decor.Footer("band", mastheadFromRole, mastheadToRole));

        return new DesignTemplate(slug(description.templateName()),
                new Theme(defaults.pageSize(), colors, textStyles, defaults.spacing()), decor);
    }

    /**
     * Snaps an extracted hex to the theme color role with the smallest RGB
     * distance — decor references roles, never literal hexes, so the draft
     * stays restylable. Invalid/missing hex → the given fallback role.
     */
    private static String nearestRole(String candidateHex, Map<String, String> colors, String fallbackRole) {
        String hex = sanitizeHex(candidateHex, null);
        if (hex == null) {
            return fallbackRole;
        }
        String best = fallbackRole;
        long bestDistance = Long.MAX_VALUE;
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            long distance = rgbDistance(hex, entry.getValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static double minContrastOverBand(String candidateHex, Map<String, String> colors,
            String fromRole, String toRole) {
        String from = colors.get(fromRole);
        String to = colors.get(toRole);
        return Math.min(contrastRatio(candidateHex, midpoint(from, to)), contrastRatio(candidateHex, to));
    }

    /** Channel-wise average of two #RRGGBB colors — the gradient's middle. */
    private static String midpoint(String hexA, String hexB) {
        int a = Integer.parseInt(hexA.substring(1), 16);
        int b = Integer.parseInt(hexB.substring(1), 16);
        int r = ((a >> 16 & 0xFF) + (b >> 16 & 0xFF)) / 2;
        int g = ((a >> 8 & 0xFF) + (b >> 8 & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return String.format("#%02X%02X%02X", r, g, bl);
    }

    private static long rgbDistance(String hexA, String hexB) {
        int a = Integer.parseInt(hexA.substring(1), 16);
        int b = Integer.parseInt(hexB.substring(1), 16);
        long dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF);
        long dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF);
        long db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }

    private static String extractedColor(StyleDescription d, String role) {
        return switch (role) {
            case "background" -> d.background();
            case "surface" -> d.surface();
            case "text" -> d.text();
            case "muted" -> d.muted();
            case "primary" -> d.primary();
            case "secondary" -> d.secondary();
            case "accent" -> d.accent();
            case "divider" -> d.divider();
            default -> null;
        };
    }

    private static String sanitizeHex(String candidate, String fallback) {
        if (candidate == null) {
            return fallback;
        }
        var matcher = HEX_COLOR.matcher(candidate.strip());
        return matcher.matches() ? "#" + matcher.group(1).toUpperCase(Locale.ROOT) : fallback;
    }

    private static String slug(String name) {
        String slug = name == null ? "" : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            return "extracted-style";
        }
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    /** WCAG contrast ratio between two #RRGGBB colors (same math as Agent #9's lint). */
    private static double contrastRatio(String hexA, String hexB) {
        double la = relativeLuminance(hexA);
        double lb = relativeLuminance(hexB);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = channel((rgb >> 16 & 0xFF) / 255.0);
        double g = channel((rgb >> 8 & 0xFF) / 255.0);
        double b = channel((rgb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
