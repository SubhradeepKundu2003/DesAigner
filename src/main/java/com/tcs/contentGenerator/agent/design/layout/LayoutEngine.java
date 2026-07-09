package com.tcs.contentGenerator.agent.design.layout;

import java.util.List;

import org.springframework.stereotype.Component;

import java.util.ArrayList;

import com.tcs.contentGenerator.agent.design.CompositionPlan;
import com.tcs.contentGenerator.agent.design.Decor;
import com.tcs.contentGenerator.agent.design.DesignTemplate;
import com.tcs.contentGenerator.agent.design.SectionComposition;
import com.tcs.contentGenerator.agent.design.SectionPattern;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.ShapeBox;
import com.tcs.contentGenerator.design.SourceLink;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;

/**
 * Turns a semantic {@link CompositionPlan} into a positioned {@link DesignDocument}:
 * measures text, positions frames from the theme's margins/gutters, paginates,
 * and clamps components that would never fit ({@link OverflowResolver}). 100%
 * deterministic — no LLM involvement, and this is the only place geometry is
 * produced.
 */
@Component
public class LayoutEngine {

    private static final double PARAGRAPH_GAP = 6;
    private static final double ITEM_GAP = 14;
    private static final double SECTION_GAP = 24;
    private static final double DIVIDER_HEIGHT = 2;
    private static final double ICON_SIZE = 10;
    /** Real icon images get slightly more room than the fallback dot. */
    private static final double ICON_IMAGE_SIZE = 12;
    private static final double ICON_GAP = 6;
    private static final int EVENT_SUMMARY_CHARS = 140;
    private static final TextStyle DEFAULT_STYLE = new TextStyle("SansSerif", 10, "normal", "text", 14);

    /** Well-known asset id the brand logo is always attached under (see {@link com.tcs.contentGenerator.agent.design.DesignCompositionAgent}). */
    public static final String BRAND_LOGO_ASSET_ID = "brand-logo";
    private static final double LOGO_SIZE = 40;
    private static final double LOGO_GAP = 12;

    /**
     * Prefix marking a decoration asset id ({@code decor-<kind>-<componentId>});
     * the composition agent finds these after layout, generates each SVG at the
     * box's exact frame size via {@code DecorPainter}, and attaches the assets.
     */
    public static final String DECOR_ASSET_PREFIX = "decor-";
    /** Extra band height beyond the measured masthead content. */
    private static final double BAND_PADDING = 16;
    private static final double CHIP_SIZE = 20;
    private static final double CARD_PADDING = 12;
    private static final double FOOTER_HEIGHT = 8;
    /** Title style used on a masthead band when the theme defines it. */
    private static final String ON_BAND_TITLE_STYLE = "IssueTitleOnBand";
    /** Optional editorial styles — used only when the theme defines them. */
    private static final String LEAD_STYLE = "BodyLead";
    private static final String KICKER_TITLE_STYLE = "SectionTitleKicker";
    private static final double HERO_PADDING = 14;
    private static final double KICKER_BAR_WIDTH = 28;
    private static final double KICKER_BAR_HEIGHT = 4;
    private static final double KICKER_BAR_GAP = 6;
    /** Vertical breathing room a section tint band adds above/below its section. */
    private static final double BAND_BLEED = 10;
    /** Reserved photo-slot geometry (filled by Agent #8, dropped if nothing fits it). */
    private static final double PHOTO_SLOT_HEIGHT = 150;
    private static final double PHOTO_SLOT_WIDTH_FRACTION = 0.62;
    /** Photo-led hero: full-width magazine photo above the headline. */
    private static final double HERO_PHOTO_HEIGHT = 210;
    /** Side-image rows (placement "side"): image column fraction + height clamp. */
    private static final double SIDE_IMAGE_WIDTH_FRACTION = 0.36;
    private static final double SIDE_IMAGE_MIN_HEIGHT = 100;
    private static final double SIDE_IMAGE_MAX_HEIGHT = 190;

    private final TextMeasurer measurer = new TextMeasurer();

    public DesignDocument layout(CompositionPlan plan, DesignTemplate template, String issueTitle, String jobId) {
        Theme theme = template.theme();
        Decor decor = template.decor();
        Paginator p = new Paginator(theme);

        placeMasthead(p, theme, decor, issueTitle);
        p.advance(SECTION_GAP);

        boolean kicker = decor != null && decor.sectionHeader() != null && decor.sectionHeader().kicker();
        // one running counter for the whole issue so side images keep alternating
        // across sections, not just within one
        java.util.concurrent.atomic.AtomicInteger sideImageCounter = new java.util.concurrent.atomic.AtomicInteger();
        List<SectionComposition> sections = plan.sections();
        for (int i = 0; i < sections.size(); i++) {
            SectionComposition section = sections.get(i);
            if (i > 0 && !kicker) {
                // kicker headers carry their own accent bar — the divider would double up
                placeDivider(p, theme);
                p.advance(PARAGRAPH_GAP);
            }
            boolean banded = decor != null && decor.sectionBand() != null && i % 2 == 1;
            int bandPage = p.pageIndex();
            int bandPosition = p.positionOnCurrentPage();
            double bandTop = p.y() - BAND_BLEED;
            placeSectionHeader(p, section, theme, decor);
            p.advance(PARAGRAPH_GAP);
            layoutSection(p, section, theme, decor, sideImageCounter);
            if (banded && p.pageIndex() == bandPage) {
                // full-bleed tint behind the whole section; a ShapeBox (not a baked
                // image) so the contrast lint can still judge text on it. Sections
                // that crossed a page break simply skip their band.
                p.insertOnPage(bandPage, bandPosition, new ShapeBox(p.nextId(), ComponentRole.DECORATION,
                        new Frame(0, Math.max(0, bandTop), theme.pageSize().widthPt(),
                                p.y() + BAND_BLEED - Math.max(0, bandTop)),
                        0, true, null, "rect", decor.sectionBand().fill()));
            }
            p.advance(SECTION_GAP);
        }

        List<Page> pages = p.finish();
        if (decor != null && decor.footer() != null) {
            pages = withFooters(pages, p, theme);
        }
        return new DesignDocument(1, 1, new DesignMeta(issueTitle, jobId), theme, List.of(), pages);
    }

    private void layoutSection(Paginator p, SectionComposition section, Theme theme, Decor decor,
            java.util.concurrent.atomic.AtomicInteger sideImageCounter) {
        boolean sideImages = decor != null && decor.photo() != null
                && "side".equals(decor.photo().placement());
        switch (section.pattern()) {
            case HERO -> layoutHero(p, section, theme, decor);
            case STAT_CALLOUT -> layoutStatCallout(p, section, theme, decor);
            case EVENT_LIST -> layoutEventList(p, section, theme);
            case TWO_COLUMN -> layoutTwoColumn(p, section, theme);
            case STANDARD -> {
                if (sideImages) {
                    layoutStandardWithSideImages(p, section, theme, sideImageCounter);
                } else {
                    layoutStandard(p, section, theme);
                }
            }
        }
        // side-image sections are already illustrated per article
        if (!(sideImages && section.pattern() == SectionPattern.STANDARD)) {
            reservePhotoSlot(p, section, decor);
        }
    }

    /**
     * Magazine rows: each article's photo sits <em>beside</em> its text,
     * alternating right/left across the issue. The photo is an empty slot the
     * graphics agent fills (source-document image first, then the section's
     * brand default) or removes. The whole row is reserved at once so a page
     * break can't separate an article from its image.
     */
    private void layoutStandardWithSideImages(Paginator p, SectionComposition section, Theme theme,
            java.util.concurrent.atomic.AtomicInteger sideImageCounter) {
        TextStyle headStyle = styleOf(theme, "Headline");
        List<GeneratedArticle> articles = section.articles();
        double gutter = theme.spacing().gutterPt();
        double imageWidth = p.contentWidth() * SIDE_IMAGE_WIDTH_FRACTION;
        double textWidth = p.contentWidth() - imageWidth - gutter;
        for (int i = 0; i < articles.size(); i++) {
            GeneratedArticle article = articles.get(i);
            SourceLink link = new SourceLink(section.section().title(), article.headline());
            boolean imageLeft = sideImageCounter.getAndIncrement() % 2 == 1;

            double headHeight = measurer.heightOf(article.headline(), headStyle, textWidth);
            BodySplit body = splitBody(article.body(), theme);
            double textHeight = headHeight + PARAGRAPH_GAP + body.height(measurer, theme, textWidth);
            double imageHeight = Math.min(SIDE_IMAGE_MAX_HEIGHT,
                    Math.max(SIDE_IMAGE_MIN_HEIGHT, textHeight));
            double rowHeight = p.reserve(Math.max(textHeight, imageHeight),
                    "side-image-row:" + article.headline());
            double y = p.y();
            double textX = imageLeft ? p.x() + imageWidth + gutter : p.x();
            double imageX = imageLeft ? p.x() : p.x() + textWidth + gutter;
            p.add(new ImageBox(p.nextId(), ComponentRole.IMAGE_PLACEHOLDER,
                    new Frame(imageX, y, imageWidth, Math.min(imageHeight, rowHeight)),
                    0, false, link, null, article.headline()));
            addTextAt(p, textX, y, textWidth, headHeight,
                    ComponentRole.ARTICLE_HEADLINE, "Headline", article.headline(), link);
            body.place(this, p, textX, y + headHeight + PARAGRAPH_GAP, textWidth, theme, link);
            p.advance(rowHeight);
            if (i < articles.size() - 1) {
                p.advance(ITEM_GAP);
            }
        }
    }

    /**
     * Reserves an <em>empty</em> image slot ({@code assetId} null) below the
     * section's content when the template has photo decor — the graphics agent
     * fills it (source-document image first, then the section's brand default,
     * then GENERIC) or removes it. Reserving in the layout, instead of
     * scavenging leftover gaps afterwards, is what guarantees an image-rich
     * page: the editorial layout is too dense to leave 48pt gaps by accident.
     * Skipped for HERO (the panel already anchors it visually) and EVENT_LIST
     * (a list, not an article).
     */
    private void reservePhotoSlot(Paginator p, SectionComposition section, Decor decor) {
        if (decor == null || decor.photo() == null || section.articles().isEmpty()
                || section.pattern() == SectionPattern.HERO
                || section.pattern() == SectionPattern.EVENT_LIST) {
            return;
        }
        GeneratedArticle article = section.articles().get(0);
        SourceLink link = new SourceLink(section.section().title(), article.headline());
        if (!p.fits(ITEM_GAP + PHOTO_SLOT_HEIGHT)) {
            // a slot that would spill onto a fresh page leaves an orphan
            // image-only page — this section just goes unillustrated
            return;
        }
        p.advance(ITEM_GAP);
        double h = p.reserve(PHOTO_SLOT_HEIGHT, "photo-slot:" + section.section().title());
        p.add(new ImageBox(p.nextId(), ComponentRole.IMAGE_PLACEHOLDER,
                new Frame(p.x(), p.y(), p.contentWidth() * PHOTO_SLOT_WIDTH_FRACTION, h),
                0, false, link, null, section.section().title()));
        p.advance(h);
    }

    /** Appends the footer band to every page — drawn over empty bottom-edge space. */
    private List<Page> withFooters(List<Page> pages, Paginator p, Theme theme) {
        List<Page> out = new ArrayList<>();
        for (Page page : pages) {
            List<com.tcs.contentGenerator.design.Component> components = new ArrayList<>(page.components());
            String id = p.nextId();
            components.add(new ImageBox(id, ComponentRole.DECORATION,
                    new Frame(0, theme.pageSize().heightPt() - FOOTER_HEIGHT,
                            theme.pageSize().widthPt(), FOOTER_HEIGHT),
                    0, true, null, DECOR_ASSET_PREFIX + "footer-" + id, "footer band"));
            out.add(new com.tcs.contentGenerator.design.Page(page.id(), components));
        }
        return out;
    }

    private void layoutStandard(Paginator p, SectionComposition section, Theme theme) {
        TextStyle headStyle = styleOf(theme, "Headline");
        List<GeneratedArticle> articles = section.articles();
        for (int i = 0; i < articles.size(); i++) {
            GeneratedArticle article = articles.get(i);
            SourceLink link = new SourceLink(section.section().title(), article.headline());
            placeText(p, ComponentRole.ARTICLE_HEADLINE, "Headline", headStyle,
                    article.headline(), link, p.contentWidth());
            p.advance(PARAGRAPH_GAP);
            placeArticleBody(p, article.body(), link, p.contentWidth(), theme);
            if (i < articles.size() - 1) {
                p.advance(ITEM_GAP);
            }
        }
    }

    private void layoutHero(Paginator p, SectionComposition section, Theme theme, Decor decor) {
        if (section.articles().isEmpty()) {
            return;
        }
        GeneratedArticle article = section.articles().get(0);
        SourceLink link = new SourceLink(section.section().title(), article.headline());
        boolean photoLed = decor != null && decor.hero() != null
                && "photo-led".equals(decor.hero().style());
        if (photoLed) {
            // magazine hero: full-width photo on top (an empty slot the graphics
            // agent fills or removes), headline and lead below — no panel
            if (p.fits(HERO_PHOTO_HEIGHT + PARAGRAPH_GAP)) {
                double h = p.reserve(HERO_PHOTO_HEIGHT, "hero-photo:" + article.headline());
                p.add(new ImageBox(p.nextId(), ComponentRole.IMAGE_PLACEHOLDER,
                        new Frame(p.x(), p.y(), p.contentWidth(), h),
                        0, false, link, null, section.section().title()));
                p.advance(h);
                p.advance(PARAGRAPH_GAP);
            }
        }
        if (decor == null || decor.hero() == null || photoLed) {
            placeText(p, ComponentRole.ARTICLE_HEADLINE, "HeroHeadline", styleOf(theme, "HeroHeadline"),
                    article.headline(), link, p.contentWidth());
            p.advance(PARAGRAPH_GAP);
            placeArticleBody(p, article.body(), link, p.contentWidth(), theme);
            return;
        }

        // hero panel: measure everything at the inset width, reserve the whole
        // block at once, paint the panel first, then the texts on top of it
        double innerWidth = p.contentWidth() - 2 * HERO_PADDING;
        TextStyle headStyle = styleOf(theme, "HeroHeadline");
        double headHeight = measurer.heightOf(article.headline(), headStyle, innerWidth);
        BodySplit body = splitBody(article.body(), theme);
        double bodyHeight = body.height(measurer, theme, innerWidth);
        double total = p.reserve(headHeight + PARAGRAPH_GAP + bodyHeight + 2 * HERO_PADDING,
                "hero-panel:" + article.headline());
        double y = p.y();
        String id = p.nextId();
        p.add(new ImageBox(id, ComponentRole.DECORATION, new Frame(p.x(), y, p.contentWidth(), total),
                0, true, null, DECOR_ASSET_PREFIX + "heropanel-" + id, "hero panel"));
        double textY = y + HERO_PADDING;
        addTextAt(p, p.x() + HERO_PADDING, textY, innerWidth, headHeight,
                ComponentRole.ARTICLE_HEADLINE, "HeroHeadline", article.headline(), link);
        textY += headHeight + PARAGRAPH_GAP;
        textY = body.place(this, p, p.x() + HERO_PADDING, textY, innerWidth, theme, link);
        p.advance(total);
    }

    /**
     * Places an article body, splitting off the first paragraph as an editorial
     * lead (larger, muted {@code BodyLead} style, role {@code ARTICLE_LEAD})
     * when the theme defines that style and the body has more than one
     * paragraph — otherwise exactly the old single-box behavior.
     */
    private void placeArticleBody(Paginator p, String text, SourceLink link, double width, Theme theme) {
        BodySplit split = splitBody(text, theme);
        if (split.lead() != null) {
            placeText(p, ComponentRole.ARTICLE_LEAD, LEAD_STYLE, styleOf(theme, LEAD_STYLE),
                    split.lead(), link, width);
            p.advance(PARAGRAPH_GAP);
        }
        placeText(p, ComponentRole.ARTICLE_BODY, "Body", styleOf(theme, "Body"),
                split.rest(), link, width);
    }

    private static BodySplit splitBody(String text, Theme theme) {
        if (!theme.textStyles().containsKey(LEAD_STYLE)) {
            return new BodySplit(null, text);
        }
        String[] parts = text.split("\n\n", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return new BodySplit(null, text);
        }
        return new BodySplit(parts[0].strip(), parts[1].strip());
    }

    /** An article body after the optional lead-paragraph split. */
    private record BodySplit(String lead, String rest) {

        double height(TextMeasurer measurer, Theme theme, double width) {
            double rest = measurer.heightOf(rest(), styleOf(theme, "Body"), width);
            return lead() == null ? rest
                    : measurer.heightOf(lead(), styleOf(theme, LEAD_STYLE), width) + PARAGRAPH_GAP + rest;
        }

        /** Places the (lead +) body at explicit coordinates; returns the y below the last box. */
        double place(LayoutEngine engine, Paginator p, double x, double y, double width,
                Theme theme, SourceLink link) {
            if (lead() != null) {
                double leadHeight = engine.measurer.heightOf(lead(), styleOf(theme, LEAD_STYLE), width);
                engine.addTextAt(p, x, y, width, leadHeight, ComponentRole.ARTICLE_LEAD, LEAD_STYLE,
                        lead(), link);
                y += leadHeight + PARAGRAPH_GAP;
            }
            double restHeight = engine.measurer.heightOf(rest(), styleOf(theme, "Body"), width);
            engine.addTextAt(p, x, y, width, restHeight, ComponentRole.ARTICLE_BODY, "Body", rest(), link);
            return y + restHeight;
        }
    }

    private void layoutEventList(Paginator p, SectionComposition section, Theme theme) {
        TextStyle style = styleOf(theme, "EventItem");
        List<GeneratedArticle> articles = section.articles();
        for (int i = 0; i < articles.size(); i++) {
            GeneratedArticle article = articles.get(i);
            SourceLink link = new SourceLink(section.section().title(), article.headline());
            String line = "• " + article.headline()
                    + (article.body().isBlank() ? "" : " — " + truncate(article.body(), EVENT_SUMMARY_CHARS));
            placeText(p, ComponentRole.ARTICLE_HEADLINE, "EventItem", style, line, link, p.contentWidth());
            if (i < articles.size() - 1) {
                p.advance(PARAGRAPH_GAP);
            }
        }
    }

    private void layoutStatCallout(Paginator p, SectionComposition section, Theme theme, Decor decor) {
        if (section.articles().isEmpty()) {
            return;
        }
        GeneratedArticle article = section.articles().get(0);
        SourceLink link = new SourceLink(section.section().title(), article.headline());

        placeText(p, ComponentRole.ARTICLE_HEADLINE, "Headline", styleOf(theme, "Headline"),
                article.headline(), link, p.contentWidth());
        p.advance(PARAGRAPH_GAP);

        boolean card = decor != null && decor.statCard() != null;
        double pad = card ? CARD_PADDING : 0;
        TextStyle statStyle = styleOf(theme, "Stat");
        TextStyle labelStyle = styleOf(theme, "StatLabel");
        double innerWidth = p.contentWidth() - 2 * pad;
        double statWidth = innerWidth * 0.35;
        double labelWidth = innerWidth - statWidth;
        double statHeight = measurer.heightOf(section.statValue(), statStyle, statWidth);
        double labelHeight = measurer.heightOf(section.statLabel(), labelStyle, labelWidth);
        double rowHeight = Math.max(statHeight, labelHeight);
        double total = p.reserve(rowHeight + 2 * pad, "stat-callout:" + article.headline());
        double y = p.y();
        if (card) {
            String id = p.nextId();
            p.add(new ImageBox(id, ComponentRole.DECORATION, new Frame(p.x(), y, p.contentWidth(), total),
                    0, true, null, DECOR_ASSET_PREFIX + "statcard-" + id, "stat card"));
        }
        addTextAt(p, p.x() + pad, y + pad, statWidth, total - 2 * pad,
                ComponentRole.STAT_VALUE, "Stat", section.statValue(), link);
        addTextAt(p, p.x() + pad + statWidth, y + pad, labelWidth, total - 2 * pad,
                ComponentRole.STAT_LABEL, "StatLabel", section.statLabel(), link);
        p.advance(total);
        p.advance(PARAGRAPH_GAP);

        placeArticleBody(p, article.body(), link, p.contentWidth(), theme);
    }

    private void layoutTwoColumn(Paginator p, SectionComposition section, Theme theme) {
        List<GeneratedArticle> articles = section.articles();
        if (articles.size() < 2) {
            layoutStandard(p, section, theme);
            return;
        }
        GeneratedArticle first = articles.get(0);
        GeneratedArticle second = articles.get(1);
        TextStyle headStyle = styleOf(theme, "Headline");
        TextStyle bodyStyle = styleOf(theme, "Body");
        double gutter = theme.spacing().gutterPt();
        double colWidth = (p.contentWidth() - gutter) / 2;

        double firstHeight = columnHeight(first, headStyle, bodyStyle, colWidth);
        double secondHeight = columnHeight(second, headStyle, bodyStyle, colWidth);
        double h = p.reserve(Math.max(firstHeight, secondHeight), "two-column:" + section.section().title());
        double y = p.y();
        placeColumn(p, first, headStyle, bodyStyle, p.x(), y, colWidth, section.section().title());
        placeColumn(p, second, headStyle, bodyStyle, p.x() + colWidth + gutter, y, colWidth, section.section().title());
        p.advance(h);
    }

    private double columnHeight(GeneratedArticle article, TextStyle headStyle, TextStyle bodyStyle, double width) {
        return measurer.heightOf(article.headline(), headStyle, width) + PARAGRAPH_GAP
                + measurer.heightOf(article.body(), bodyStyle, width);
    }

    private void placeColumn(Paginator p, GeneratedArticle article, TextStyle headStyle, TextStyle bodyStyle,
            double x, double y, double width, String sectionTitle) {
        SourceLink link = new SourceLink(sectionTitle, article.headline());
        double headHeight = measurer.heightOf(article.headline(), headStyle, width);
        addTextAt(p, x, y, width, headHeight, ComponentRole.ARTICLE_HEADLINE, "Headline", article.headline(), link);
        double bodyHeight = measurer.heightOf(article.body(), bodyStyle, width);
        addTextAt(p, x, y + headHeight + PARAGRAPH_GAP, width, bodyHeight,
                ComponentRole.ARTICLE_BODY, "Body", article.body(), link);
    }

    private void placeMasthead(Paginator p, Theme theme, Decor decor, String issueTitle) {
        Decor.Masthead band = decor == null ? null : decor.masthead();
        String titleStyleRef = band != null && theme.textStyles().containsKey(ON_BAND_TITLE_STYLE)
                ? ON_BAND_TITLE_STYLE : "IssueTitle";
        TextStyle style = styleOf(theme, titleStyleRef);
        double textWidth = p.contentWidth() - LOGO_SIZE - LOGO_GAP;
        double textHeight = measurer.heightOf(issueTitle, style, textWidth);
        double h = p.reserve(Math.max(textHeight, LOGO_SIZE), "masthead");
        double y = p.y();
        double bandHeight = 0;
        if (band != null) {
            // full-bleed band behind the logo + title, tall enough for the content
            bandHeight = Math.max(band.heightPt(), y + h + BAND_PADDING);
            String id = p.nextId();
            p.add(new ImageBox(id, ComponentRole.DECORATION,
                    new Frame(0, 0, theme.pageSize().widthPt(), bandHeight),
                    0, true, null, DECOR_ASSET_PREFIX + "masthead-" + id, "masthead band"));
        }
        p.add(new ImageBox(p.nextId(), ComponentRole.LOGO, new Frame(p.x(), y, LOGO_SIZE, LOGO_SIZE),
                0, true, null, BRAND_LOGO_ASSET_ID, "Company logo"));
        p.add(new TextBox(p.nextId(), ComponentRole.ISSUE_TITLE,
                new Frame(p.x() + LOGO_SIZE + LOGO_GAP, y, textWidth, h), 0, false, null,
                titleStyleRef, issueTitle));
        p.advance(h);
        if (bandHeight > p.y()) {
            p.advance(bandHeight - p.y());
        }
    }

    private void placeSectionHeader(Paginator p, SectionComposition section, Theme theme, Decor decor) {
        boolean hasIconAsset = section.iconAssetId() != null;
        boolean chip = decor != null && decor.sectionHeader() != null
                && decor.sectionHeader().style() != null
                && decor.sectionHeader().style().startsWith("chip");
        boolean kicker = decor != null && decor.sectionHeader() != null && decor.sectionHeader().kicker();
        String titleStyleRef = kicker && theme.textStyles().containsKey(KICKER_TITLE_STYLE)
                ? KICKER_TITLE_STYLE : "SectionTitle";
        TextStyle style = styleOf(theme, titleStyleRef);
        String title = kicker
                ? section.section().title().toUpperCase(java.util.Locale.ROOT)
                : section.section().title();
        double iconSize = hasIconAsset ? ICON_IMAGE_SIZE : ICON_SIZE;
        double leadSize = chip ? CHIP_SIZE : iconSize;
        double barBlock = kicker ? KICKER_BAR_HEIGHT + KICKER_BAR_GAP : 0;
        double textWidth = p.contentWidth() - leadSize - ICON_GAP;
        double textHeight = measurer.heightOf(title, style, textWidth);
        // reserve bar + header row together so a page break can't split them
        double h = p.reserve(barBlock + Math.max(textHeight, leadSize),
                "section-title:" + section.section().title()) - barBlock;
        if (kicker) {
            p.add(new ShapeBox(p.nextId(), ComponentRole.DIVIDER,
                    new Frame(p.x(), p.y(), KICKER_BAR_WIDTH, KICKER_BAR_HEIGHT),
                    0, false, null, "rect", decor.sectionHeader().colorRole()));
            p.advance(barBlock);
        }
        double y = p.y();
        if (chip) {
            String id = p.nextId();
            p.add(new ImageBox(id, ComponentRole.DECORATION, new Frame(p.x(), y, CHIP_SIZE, CHIP_SIZE),
                    0, true, null, DECOR_ASSET_PREFIX + "chip-" + id, "section chip"));
        }
        double iconX = p.x() + (leadSize - iconSize) / 2;
        double iconY = y + (leadSize - iconSize) / 2;
        if (hasIconAsset) {
            p.add(new ImageBox(p.nextId(), ComponentRole.SECTION_ICON, new Frame(iconX, iconY, iconSize, iconSize),
                    0, true, null, section.iconAssetId(), section.section().title() + " icon"));
        } else {
            p.add(new ShapeBox(p.nextId(), ComponentRole.SECTION_ICON, new Frame(iconX, iconY, iconSize, iconSize),
                    0, false, null, "circle", section.iconColorRole()));
        }
        p.add(new TextBox(p.nextId(), ComponentRole.SECTION_TITLE,
                new Frame(p.x() + leadSize + ICON_GAP, y, textWidth, h), 0, false, null,
                titleStyleRef, title));
        p.advance(h);
    }

    private void placeDivider(Paginator p, Theme theme) {
        double h = p.reserve(DIVIDER_HEIGHT, "divider");
        p.add(new ShapeBox(p.nextId(), ComponentRole.DIVIDER, new Frame(p.x(), p.y(), p.contentWidth(), h),
                0, false, null, "rect", "divider"));
        p.advance(h);
    }

    private void placeText(Paginator p, ComponentRole role, String styleRef, TextStyle style, String text,
            SourceLink source, double width) {
        double natural = measurer.heightOf(text, style, width);
        double h = p.reserve(natural, styleRef);
        addTextAt(p, p.x(), p.y(), width, h, role, styleRef, text, source);
        p.advance(h);
    }

    private void addTextAt(Paginator p, double x, double y, double width, double height, ComponentRole role,
            String styleRef, String text, SourceLink source) {
        p.add(new TextBox(p.nextId(), role, new Frame(x, y, width, height), 0, false, source, styleRef, text));
    }

    private static String truncate(String text, int max) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max - 1).stripTrailing() + "…";
    }

    private static TextStyle styleOf(Theme theme, String name) {
        TextStyle style = theme.textStyles().get(name);
        return style != null ? style : DEFAULT_STYLE;
    }
}
