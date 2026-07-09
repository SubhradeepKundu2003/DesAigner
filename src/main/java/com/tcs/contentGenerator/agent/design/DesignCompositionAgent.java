package com.tcs.contentGenerator.agent.design;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.design.decor.DecorPainter;
import com.tcs.contentGenerator.agent.design.layout.LayoutEngine;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.Colors;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Agent #7 of the pipeline. Turns the compliance-corrected {@link GeneratedNewsletter}
 * into a positioned {@link DesignDocument}: two deterministic halves —
 * <em>composition</em> (semantic — pick a {@link SectionPattern} per section,
 * match a section icon color, pull a key metric into a stat callout where one
 * fits) here, and <em>layout</em> (geometric — text measurement, frame
 * positioning, pagination) in {@link LayoutEngine}. The LLM never produces
 * geometry; pattern selection is rule-based, not a model call, so this stage
 * has no LLM cost.
 */
@Component
@Order(7)
public class DesignCompositionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DesignCompositionAgent.class);
    /** Same figure shape used by fact validation and brand compliance. */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private static final String BRAND_LOGO_FOLDER = "BRAND";
    /** Chosen per theme: black on light page backgrounds (brand-guide preference),
     *  white on dark ones (e.g. the extracted {@code noir-luxe} template). */
    private static final String BRAND_LOGO_LIGHT_BG = "logo_black.svg";
    private static final String BRAND_LOGO_DARK_BG = "logo_white.svg";
    /** Section icon files: {@code assets/ICONS/<NewsletterSection name>.<ext>} — see {@link IconMatcher}. */
    private static final String ICONS_FOLDER = "ICONS";

    private final TemplateCatalog templates;
    private final LayoutEngine layoutEngine;
    private final StorageService storage;
    private final String brandAssetsRoot;

    public DesignCompositionAgent(TemplateCatalog templates, LayoutEngine layoutEngine, StorageService storage,
            @Value("${app.graphics.brand-assets-root:assets}") String brandAssetsRoot) {
        this.templates = templates;
        this.layoutEngine = layoutEngine;
        this.storage = storage;
        this.brandAssetsRoot = brandAssetsRoot;
    }

    @Override
    public String name() {
        return "Design Composition Agent";
    }

    @Override
    public void execute(PipelineContext context) {
        GeneratedNewsletter newsletter = context.getGeneratedNewsletter();
        if (newsletter == null || newsletter.sections().isEmpty()) {
            log.info("No generated newsletter to compose, skipping design composition");
            return;
        }

        DesignTemplate template = templates.getDefault();
        boolean darkBackground = Colors.isDark(template.theme().colors().get("background"));
        // The section icon files are black stroke SVGs — invisible on a dark page.
        // Dark themes keep the colored-dot fallback (theme roles, always visible).
        Map<NewsletterSection, String> iconRefs = darkBackground ? Map.of() : listSectionIcons();
        if (darkBackground) {
            log.info("Dark page background — using dot section icons");
        }
        List<SectionComposition> sections = newsletter.sections().stream()
                .filter(section -> !section.articles().isEmpty())
                .map(section -> compose(section, iconRefs))
                .toList();
        CompositionPlan plan = new CompositionPlan(template.name(), sections);
        DesignDocument document = layoutEngine.layout(plan, template, newsletter.issueTitle(), context.getJobId());
        List<Asset> assets = new ArrayList<>();
        // The logo sits on the masthead band when the template has one, on the
        // page otherwise — pick the black/white variant against that backdrop.
        String logoBackdrop = template.decor() != null && template.decor().masthead() != null
                ? template.theme().colors().get(template.decor().masthead().from())
                : template.theme().colors().get("background");
        assets.add(new Asset(LayoutEngine.BRAND_LOGO_ASSET_ID, "image",
                brandAssetsRoot + "/" + BRAND_LOGO_FOLDER + "/"
                        + (Colors.isDark(logoBackdrop) ? BRAND_LOGO_DARK_BG : BRAND_LOGO_LIGHT_BG), null, null));
        for (SectionComposition section : sections) {
            if (section.iconAssetId() != null) {
                assets.add(new Asset(section.iconAssetId(), "image", iconRefs.get(section.section()), null, null));
            }
        }
        assets.addAll(decorAssets(document, template, context.getJobId()));
        document = new DesignDocument(document.schemaVersion(), document.revision(), document.meta(),
                document.theme(), List.copyOf(assets), document.pages());
        context.setDesignDocument(document);
        log.info("Design composition produced {} page(s) from {} section(s) using template '{}'",
                document.pages().size(), sections.size(), template.name());
    }

    private SectionComposition compose(GeneratedSection section, Map<NewsletterSection, String> iconRefs) {
        NewsletterSection newsletterSection = section.section();
        List<GeneratedArticle> articles = section.articles();
        String iconColorRole = IconMatcher.colorRoleFor(newsletterSection);
        String iconAssetId = iconRefs.containsKey(newsletterSection)
                ? IconMatcher.assetIdFor(newsletterSection) : null;

        if (newsletterSection == NewsletterSection.LEADERSHIP_MESSAGE) {
            return new SectionComposition(newsletterSection, SectionPattern.HERO, articles, iconColorRole,
                    iconAssetId, null, null);
        }
        if (newsletterSection == NewsletterSection.UPCOMING_EVENTS) {
            return new SectionComposition(newsletterSection, SectionPattern.EVENT_LIST, articles, iconColorRole,
                    iconAssetId, null, null);
        }
        if (articles.size() == 1) {
            String[] stat = extractStat(articles.get(0));
            if (stat != null) {
                return new SectionComposition(newsletterSection, SectionPattern.STAT_CALLOUT, articles,
                        iconColorRole, iconAssetId, stat[0], stat[1]);
            }
        }
        if (articles.size() == 2) {
            return new SectionComposition(newsletterSection, SectionPattern.TWO_COLUMN, articles, iconColorRole,
                    iconAssetId, null, null);
        }
        return new SectionComposition(newsletterSection, SectionPattern.STANDARD, articles, iconColorRole,
                iconAssetId, null, null);
    }

    /**
     * One storage listing per run: every file under {@code assets/ICONS/} whose
     * basename (case-insensitive, any extension) is a {@link NewsletterSection}
     * name becomes that section's icon. Missing folder or unmatched files are
     * simply skipped — sections without an icon keep the colored-dot fallback.
     */
    private Map<NewsletterSection, String> listSectionIcons() {
        Map<NewsletterSection, String> refs = new EnumMap<>(NewsletterSection.class);
        for (String ref : storage.list(brandAssetsRoot + "/" + ICONS_FOLDER)) {
            String base = ref.substring(ref.lastIndexOf('/') + 1);
            int dot = base.lastIndexOf('.');
            String name = (dot > 0 ? base.substring(0, dot) : base).toUpperCase(Locale.ROOT);
            try {
                refs.putIfAbsent(NewsletterSection.valueOf(name), ref);
            } catch (IllegalArgumentException notASectionName) {
                log.debug("Ignoring non-section file in {}: {}", ICONS_FOLDER, ref);
            }
        }
        return refs;
    }

    /**
     * The layout engine places decoration {@code ImageBox}es under well-known
     * {@code decor-<kind>-<cmpId>} asset ids; here each one gets its SVG
     * generated at the box's exact frame size ({@link DecorPainter}), stored
     * under the job's {@code decor/} folder, and attached as an {@link Asset}.
     * Rendering an unknown kind falls back to the renderers' placeholder box
     * (asset simply not attached) rather than failing the run.
     */
    private List<Asset> decorAssets(DesignDocument document, DesignTemplate template, String jobId) {
        Decor decor = template.decor();
        if (decor == null) {
            return List.of();
        }
        List<Asset> assets = new ArrayList<>();
        for (Page page : document.pages()) {
            for (var component : page.components()) {
                if (!(component instanceof ImageBox box)
                        || box.assetId() == null
                        || !box.assetId().startsWith(LayoutEngine.DECOR_ASSET_PREFIX)) {
                    continue;
                }
                String kind = box.assetId().substring(LayoutEngine.DECOR_ASSET_PREFIX.length())
                        .split("-", 2)[0];
                String svg = switch (kind) {
                    case "masthead" -> DecorPainter.masthead(decor.masthead(), template.theme(),
                            box.frame().w(), box.frame().h());
                    case "heropanel" -> DecorPainter.heroPanel(decor.hero(), template.theme(),
                            box.frame().w(), box.frame().h());
                    case "chip" -> DecorPainter.chip(decor.sectionHeader(), template.theme(),
                            box.frame().w(), box.frame().h());
                    case "statcard" -> DecorPainter.statCard(decor.statCard(), template.theme(),
                            box.frame().w(), box.frame().h());
                    case "footer" -> DecorPainter.footer(decor.footer(), template.theme(),
                            box.frame().w(), box.frame().h());
                    default -> null;
                };
                if (svg == null) {
                    log.warn("Unknown decoration kind in asset id {} — leaving it unresolved", box.assetId());
                    continue;
                }
                String ref = storage.store("jobs/" + jobId + "/decor/" + box.assetId() + ".svg",
                        svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assets.add(new Asset(box.assetId(), "image", ref, null, null));
            }
        }
        return assets;
    }

    /**
     * {@code {value, label}} pulled from the article's first key metric (via its
     * source {@link com.tcs.contentGenerator.agent.understanding.ContentItem}),
     * or {@code null} if there is no source or no metric contains a number.
     */
    private String[] extractStat(GeneratedArticle article) {
        if (article.source() == null) {
            return null;
        }
        List<String> metrics = article.source().item().keyMetrics();
        if (metrics.isEmpty()) {
            return null;
        }
        String metric = metrics.get(0);
        Matcher matcher = NUMBER.matcher(metric);
        if (!matcher.find()) {
            return null;
        }
        return new String[] {matcher.group(), metric};
    }
}
