package com.tcs.contentGenerator.agent.design.layout;

import java.util.List;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Locale;

import com.tcs.contentGenerator.agent.design.CompositionPlan;
import com.tcs.contentGenerator.agent.design.Decor;
import com.tcs.contentGenerator.agent.design.DesignTemplate;
import com.tcs.contentGenerator.agent.design.SectionComposition;
import com.tcs.contentGenerator.agent.design.SectionPattern;
import com.tcs.contentGenerator.agent.design.infographic.InfographicPainter;
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
    /** Gap between a KPI tile's big value and its label below it. */
    private static final double KPI_VALUE_LABEL_GAP = 2;
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
    /** Cover asset id for the logo variant picked against the cover fill. */
    public static final String COVER_LOGO_ASSET_ID = "brand-logo-cover";
    /** Cover display styles — used when the theme defines them. */
    private static final String COVER_TITLE_STYLE = "CoverTitle";
    private static final String COVER_TITLE_ACCENT_STYLE = "CoverTitleAccent";
    private static final String COVER_SUBTITLE_STYLE = "CoverSubtitle";
    /**
     * Infographic point styles — fall back to Headline/Body when a theme lacks
     * them. Bar-shaped rows (numberedBars/chevronBars) sit on a saturated
     * {@code primary}-role fill, so their style is light text; card-shaped
     * grids sit on the light {@code surface} fill, so they need their own
     * dark-text pair rather than reusing the row styles (a fixed white on a
     * light card would be unreadable).
     */
    private static final String INFOGRAPHIC_LABEL_STYLE = "InfographicLabel";
    private static final String INFOGRAPHIC_TEXT_STYLE = "InfographicBody";
    private static final String INFOGRAPHIC_CARD_LABEL_STYLE = "InfographicCardLabel";
    private static final String INFOGRAPHIC_CARD_TEXT_STYLE = "InfographicCardBody";
    /** Vertical gap between an infographic point's label and its one-liner. */
    private static final double INFO_LABEL_TEXT_GAP = 2;
    /** Vertical gap between infographic rows. */
    private static final double INFO_ROW_GAP = 10;
    /** Shape kinds {@link #layoutBarRows} knows how to paint (disc + bar). */
    private static final java.util.Set<String> KNOWN_ROW_KINDS = java.util.Set.of("numberedBars", "chevronBars");

    private final TextMeasurer measurer = new TextMeasurer();

    public DesignDocument layout(CompositionPlan plan, DesignTemplate template, String issueTitle, String jobId) {
        Theme theme = template.theme();
        Decor decor = template.decor();
        Paginator p = new Paginator(theme);

        boolean hasCover = decor != null && decor.cover() != null;
        if (hasCover) {
            placeCover(p, theme, decor.cover(), issueTitle,
                    plan.sections().isEmpty() ? null : plan.sections().get(0));
            p.newPage();
        }
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
            boolean perSectionTint = decor != null && decor.sectionBand() != null
                    && "per-section".equals(decor.sectionBand().style());
            // per-section: every section is tinted; alternating: every other one
            boolean banded = decor != null && decor.sectionBand() != null
                    && (perSectionTint || i % 2 == 1);
            int bandPage = p.pageIndex();
            int bandPosition = p.positionOnCurrentPage();
            double bandTop = p.y() - BAND_BLEED;
            placeSectionHeader(p, section, theme, decor);
            p.advance(PARAGRAPH_GAP);
            layoutSection(p, section, theme, decor, sideImageCounter);
            if (banded && p.pageIndex() == bandPage) {
                // full-bleed tint behind the whole section. Sections that crossed a
                // page break simply skip their band.
                Frame bandFrame = new Frame(0, Math.max(0, bandTop), theme.pageSize().widthPt(),
                        p.y() + BAND_BLEED - Math.max(0, bandTop));
                if (perSectionTint) {
                    // a baked SVG tinted by this section's own type color — the role
                    // is carried in the asset id so the composition agent can paint it
                    String id = p.nextId();
                    p.insertOnPage(bandPage, bandPosition, new ImageBox(id, ComponentRole.DECORATION, bandFrame,
                            0, true, null, DECOR_ASSET_PREFIX + "sectiontint-" + section.iconColorRole() + "-" + id,
                            "section tint"));
                } else {
                    // solid ShapeBox (not a baked image) so the contrast lint can still judge text on it
                    p.insertOnPage(bandPage, bandPosition, new ShapeBox(p.nextId(), ComponentRole.DECORATION,
                            bandFrame, 0, true, null, "rect", decor.sectionBand().fill()));
                }
            }
            p.advance(SECTION_GAP);
        }

        List<Page> pages = p.finish();
        if (decor != null && decor.footer() != null) {
            pages = withFooters(pages, p, theme, hasCover);
        }
        return new DesignDocument(1, 1, new DesignMeta(issueTitle, jobId), theme, List.of(), pages);
    }

    /**
     * Dedicated magazine cover: full-bleed fill, brand logo top-left, a large
     * rounded photo (an empty DECORATION slot — it may bleed past the margins —
     * that the graphics agent fills with the lead section's image), the issue
     * title split into a right-aligned display block ("TD Monthly" /
     * "NEWSLETTER" in the accent style / the issue date as subtitle), and a
     * decorative wave band along the bottom edge.
     */
    private void placeCover(Paginator p, Theme theme, Decor.Cover cover, String issueTitle,
            SectionComposition leadSection) {
        double pageW = theme.pageSize().widthPt();
        double pageH = theme.pageSize().heightPt();
        double margin = theme.spacing().marginPt();

        p.add(new ShapeBox(p.nextId(), ComponentRole.DECORATION, new Frame(0, 0, pageW, pageH),
                0, true, null, "rect", cover.fill()));
        p.add(new ImageBox(p.nextId(), ComponentRole.LOGO, new Frame(margin, margin, 46, 46),
                0, true, null, COVER_LOGO_ASSET_ID, "Company logo"));

        SourceLink link = leadSection == null || leadSection.articles().isEmpty() ? null
                : new SourceLink(leadSection.section().title(), leadSection.articles().get(0).headline());
        if (link != null) {
            p.add(new ImageBox(p.nextId(), ComponentRole.DECORATION,
                    new Frame(0, margin + 72, pageW * 0.86, pageH * 0.4), 0, false, link, null, "cover photo"));
        }

        // "TD Monthly Newsletter — July 2026" → "TD Monthly" / "NEWSLETTER" / "July 2026"
        String main = issueTitle;
        String subtitle = null;
        int dash = issueTitle.indexOf('—') >= 0 ? issueTitle.indexOf('—') : issueTitle.indexOf(" - ");
        if (dash > 0) {
            main = issueTitle.substring(0, dash).strip();
            subtitle = issueTitle.substring(dash + 1).strip();
        }
        String accentLine = null;
        if (main.toLowerCase(Locale.ROOT).endsWith("newsletter")) {
            accentLine = "NEWSLETTER";
            main = main.substring(0, main.length() - "newsletter".length()).strip();
        }

        TextStyle titleStyle = styleOf(theme, COVER_TITLE_STYLE);
        double y = margin + 72 + pageH * 0.4 + 48;
        double titleH = measurer.heightOf(main, titleStyle, p.contentWidth());
        p.add(new TextBox(p.nextId(), ComponentRole.ISSUE_TITLE,
                new Frame(margin, y, p.contentWidth(), titleH), 0, false, null, COVER_TITLE_STYLE, main));
        y += titleH + 2;
        if (accentLine != null) {
            TextStyle accentStyle = styleOf(theme, COVER_TITLE_ACCENT_STYLE);
            double accentH = measurer.heightOf(accentLine, accentStyle, p.contentWidth());
            p.add(new TextBox(p.nextId(), ComponentRole.ISSUE_TITLE,
                    new Frame(margin, y, p.contentWidth(), accentH), 0, false, null,
                    COVER_TITLE_ACCENT_STYLE, accentLine));
            y += accentH + 10;
        }
        if (subtitle != null && !subtitle.isBlank()) {
            TextStyle subStyle = styleOf(theme, COVER_SUBTITLE_STYLE);
            double subW = p.contentWidth() * 0.7;
            double subH = measurer.heightOf(subtitle, subStyle, subW);
            p.add(new TextBox(p.nextId(), ComponentRole.ISSUE_TITLE,
                    new Frame(margin + p.contentWidth() - subW, y, subW, subH), 0, false, null,
                    COVER_SUBTITLE_STYLE, subtitle));
        }

        String wavesId = p.nextId();
        p.add(new ImageBox(wavesId, ComponentRole.DECORATION,
                new Frame(0, pageH - 120, pageW, 120), 0, true, null,
                DECOR_ASSET_PREFIX + "coverwaves-" + wavesId, "wave band"));
    }

    private void layoutSection(Paginator p, SectionComposition section, Theme theme, Decor decor,
            java.util.concurrent.atomic.AtomicInteger sideImageCounter) {
        boolean sideImages = decor != null && decor.photo() != null
                && "side".equals(decor.photo().placement());
        boolean cards = decor != null && decor.cards() != null
                && section.pattern() == SectionPattern.STANDARD && section.articles().size() >= 3;
        switch (section.pattern()) {
            case HERO -> layoutHero(p, section, theme, decor);
            case STAT_CALLOUT -> layoutStatCallout(p, section, theme, decor);
            case KPI_TILES -> layoutKpiTiles(p, section, theme, decor);
            case INFOGRAPHIC -> layoutInfographic(p, section, theme);
            case EVENT_LIST -> layoutEventList(p, section, theme);
            case TWO_COLUMN -> layoutTwoColumn(p, section, theme);
            case STANDARD -> {
                if (cards) {
                    layoutStandardAsCards(p, section, theme, decor.cards());
                } else if (sideImages) {
                    layoutStandardWithSideImages(p, section, theme, sideImageCounter);
                } else {
                    layoutStandard(p, section, theme);
                }
            }
        }
        // side-image and card sections are already visually treated per article
        if (!((sideImages || cards) && section.pattern() == SectionPattern.STANDARD)) {
            reservePhotoSlot(p, section, decor);
        }
    }

    /**
     * Card grid for dense sections (3+ articles): two rounded shadowed cards
     * per row (equal height — the taller cell wins), an odd trailing article
     * gets a full-width card. Cards carry headline + body only; they replace
     * the section's photo treatment, keeping dense sections compact.
     */
    private void layoutStandardAsCards(Paginator p, SectionComposition section, Theme theme,
            Decor.Cards cards) {
        TextStyle headStyle = styleOf(theme, "Headline");
        TextStyle bodyStyle = styleOf(theme, "Body");
        List<GeneratedArticle> articles = section.articles();
        double gutter = theme.spacing().gutterPt();
        for (int i = 0; i < articles.size(); i += 2) {
            boolean pair = i + 1 < articles.size();
            double cardWidth = pair ? (p.contentWidth() - gutter) / 2 : p.contentWidth();
            double innerWidth = cardWidth - 2 * CARD_PADDING;

            GeneratedArticle left = articles.get(i);
            double leftHeight = cardContentHeight(left, headStyle, bodyStyle, innerWidth);
            double rowContentHeight = leftHeight;
            if (pair) {
                rowContentHeight = Math.max(rowContentHeight,
                        cardContentHeight(articles.get(i + 1), headStyle, bodyStyle, innerWidth));
            }
            double cardHeight = p.reserve(rowContentHeight + 2 * CARD_PADDING,
                    "card-row:" + section.section().title());
            double y = p.y();
            placeCard(p, left, section, theme, headStyle, bodyStyle, p.x(), y, cardWidth, cardHeight, cards);
            if (pair) {
                placeCard(p, articles.get(i + 1), section, theme, headStyle, bodyStyle,
                        p.x() + cardWidth + gutter, y, cardWidth, cardHeight, cards);
            }
            p.advance(cardHeight);
            if (i + 2 < articles.size()) {
                p.advance(ITEM_GAP);
            }
        }
    }

    private double cardContentHeight(GeneratedArticle article, TextStyle headStyle, TextStyle bodyStyle,
            double innerWidth) {
        return measurer.heightOf(article.headline(), headStyle, innerWidth) + PARAGRAPH_GAP
                + measurer.heightOf(article.body(), bodyStyle, innerWidth);
    }

    private void placeCard(Paginator p, GeneratedArticle article, SectionComposition section, Theme theme,
            TextStyle headStyle, TextStyle bodyStyle, double x, double y, double width, double height,
            Decor.Cards cards) {
        SourceLink link = new SourceLink(section.section().title(), article.headline());
        String id = p.nextId();
        p.add(new ImageBox(id, ComponentRole.DECORATION, new Frame(x, y, width, height),
                0, true, null, DECOR_ASSET_PREFIX + "card-" + id, "card"));
        double innerWidth = width - 2 * CARD_PADDING;
        double headHeight = measurer.heightOf(article.headline(), headStyle, innerWidth);
        addTextAt(p, x + CARD_PADDING, y + CARD_PADDING, innerWidth, headHeight,
                ComponentRole.ARTICLE_HEADLINE, "Headline", article.headline(), link);
        double bodyHeight = measurer.heightOf(article.body(), bodyStyle, innerWidth);
        addTextAt(p, x + CARD_PADDING, y + CARD_PADDING + headHeight + PARAGRAPH_GAP, innerWidth,
                Math.min(bodyHeight, height - 2 * CARD_PADDING - headHeight - PARAGRAPH_GAP),
                ComponentRole.ARTICLE_BODY, "Body", article.body(), link);
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
                || section.pattern() == SectionPattern.EVENT_LIST
                // the infographic IS the section's visual — a photo below would crowd it
                || section.pattern() == SectionPattern.INFOGRAPHIC) {
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

    /** Appends the footer band to every page (the cover carries its own wave band instead). */
    private List<Page> withFooters(List<Page> pages, Paginator p, Theme theme, boolean skipFirst) {
        List<Page> out = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            if (skipFirst && i == 0) {
                out.add(page);
                continue;
            }
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

    /**
     * KPI tiles: a single article's two-or-more numeric metrics laid out as a
     * horizontal row of tiles (big value over a short label), the article
     * headline above and body below — the infographic sibling of
     * {@link #layoutStatCallout}. Each tile reuses the stat-card decoration
     * ({@code decor-statcard-*}, drawn only when the template defines
     * {@code statCard}) and the {@code STAT_VALUE}/{@code STAT_LABEL} roles, so
     * the renderers and the review agent's contrast lint already handle it. The
     * tiles are centered when the theme defines the optional {@code Kpi}/
     * {@code KpiLabel} styles, else they fall back to {@code Stat}/{@code StatLabel}.
     */
    private void layoutKpiTiles(Paginator p, SectionComposition section, Theme theme, Decor decor) {
        if (section.articles().isEmpty() || section.stats().size() < 2) {
            return;
        }
        GeneratedArticle article = section.articles().get(0);
        SourceLink link = new SourceLink(section.section().title(), article.headline());

        placeText(p, ComponentRole.ARTICLE_HEADLINE, "Headline", styleOf(theme, "Headline"),
                article.headline(), link, p.contentWidth());
        p.advance(PARAGRAPH_GAP);

        List<SectionComposition.Stat> stats = section.stats();
        int n = stats.size();
        boolean card = decor != null && decor.statCard() != null;
        double pad = card ? CARD_PADDING : 0;
        double gutter = theme.spacing().gutterPt();
        double tileWidth = (p.contentWidth() - (n - 1) * gutter) / n;
        double innerWidth = tileWidth - 2 * pad;
        String valueRef = theme.textStyles().containsKey("Kpi") ? "Kpi" : "Stat";
        String labelRef = theme.textStyles().containsKey("KpiLabel") ? "KpiLabel" : "StatLabel";
        TextStyle valueStyle = styleOf(theme, valueRef);
        TextStyle labelStyle = styleOf(theme, labelRef);

        // uniform tile height so the row reads as one band, whatever each tile measures
        double content = 0;
        for (SectionComposition.Stat stat : stats) {
            content = Math.max(content, measurer.heightOf(stat.value(), valueStyle, innerWidth)
                    + KPI_VALUE_LABEL_GAP + measurer.heightOf(stat.label(), labelStyle, innerWidth));
        }
        double total = p.reserve(content + 2 * pad, "kpi-tiles:" + section.section().title());
        double y = p.y();
        double x = p.x();
        for (SectionComposition.Stat stat : stats) {
            if (card) {
                String id = p.nextId();
                p.add(new ImageBox(id, ComponentRole.DECORATION, new Frame(x, y, tileWidth, total),
                        0, true, null, DECOR_ASSET_PREFIX + "statcard-" + id, "kpi tile"));
            }
            double valueHeight = measurer.heightOf(stat.value(), valueStyle, innerWidth);
            addTextAt(p, x + pad, y + pad, innerWidth, valueHeight,
                    ComponentRole.STAT_VALUE, valueRef, stat.value(), link);
            double labelHeight = measurer.heightOf(stat.label(), labelStyle, innerWidth);
            addTextAt(p, x + pad, y + pad + valueHeight + KPI_VALUE_LABEL_GAP, innerWidth, labelHeight,
                    ComponentRole.STAT_LABEL, labelRef, stat.label(), link);
            x += tileWidth + gutter;
        }
        p.advance(total);
        p.advance(PARAGRAPH_GAP);

        placeArticleBody(p, article.body(), link, p.contentWidth(), theme);
    }

    /**
     * Infographic section: the article headline, then the chosen design's
     * shapes with the article's points as real text on top, then the body
     * prose below (same envelope as {@link #layoutKpiTiles}). The shapes are
     * per-row {@code decor-infographic-*} DECORATION boxes regenerated by the
     * composition agent via {@code InfographicPainter} — the drawing params
     * ride in the asset id ({@code InfographicPainter.encode}) because that id
     * is all the asset generator gets. Unknown shape kinds simply skip the
     * graphic (points still read as label/text rows) — a broken spec can never
     * lose content.
     */
    private void layoutInfographic(Paginator p, SectionComposition section, Theme theme) {
        if (section.articles().isEmpty() || section.infographic() == null
                || section.points().size() < 2) {
            layoutStandard(p, section, theme);
            return;
        }
        GeneratedArticle article = section.articles().get(0);
        SourceLink link = new SourceLink(section.section().title(), article.headline());

        placeText(p, ComponentRole.ARTICLE_HEADLINE, "Headline", styleOf(theme, "Headline"),
                article.headline(), link, p.contentWidth());
        p.advance(PARAGRAPH_GAP);

        if ("pointCard".equals(section.infographic().shape().kind())) {
            layoutCardGrid(p, section, theme, link);
        } else {
            // numberedBars/chevronBars share this row geometry — only the SVG
            // differs, resolved by InfographicPainter.paint from the shape kind
            // encoded in the asset id. An unknown kind still lays out the text
            // rows, just without the painted shape (a broken spec loses only
            // its graphic, never the content).
            boolean drawShapes = KNOWN_ROW_KINDS.contains(section.infographic().shape().kind());
            layoutBarRows(p, section, theme, link, drawShapes);
        }
        p.advance(PARAGRAPH_GAP);

        placeArticleBody(p, article.body(), link, p.contentWidth(), theme);
    }

    /**
     * The NUMBERED_LIST/KPI_BARS row shape: each point is a full-width bar
     * (painted by {@code InfographicPainter.numberedBars}/{@code chevronBars})
     * carrying its label and one-liner as text boxes, with a numbered disc on
     * the left. Rows reserve individually so a long list paginates between
     * rows, never through one.
     */
    private void layoutBarRows(Paginator p, SectionComposition section, Theme theme,
            SourceLink link, boolean drawShapes) {
        String labelRef = theme.textStyles().containsKey(INFOGRAPHIC_LABEL_STYLE)
                ? INFOGRAPHIC_LABEL_STYLE : "Headline";
        String textRef = theme.textStyles().containsKey(INFOGRAPHIC_TEXT_STYLE)
                ? INFOGRAPHIC_TEXT_STYLE : "Body";
        TextStyle labelStyle = styleOf(theme, labelRef);
        TextStyle textStyle = styleOf(theme, textRef);
        double textX = p.x() + InfographicPainter.DISC + InfographicPainter.DISC_GAP;
        double textWidth = p.contentWidth() - InfographicPainter.DISC - InfographicPainter.DISC_GAP
                - InfographicPainter.BAR_PADDING;

        List<GeneratedArticle.Point> points = section.points();
        for (int i = 0; i < points.size(); i++) {
            GeneratedArticle.Point point = points.get(i);
            double labelHeight = measurer.heightOf(point.label(), labelStyle, textWidth);
            double textHeight = point.text().isBlank() ? 0
                    : measurer.heightOf(point.text(), textStyle, textWidth);
            double content = labelHeight
                    + (textHeight > 0 ? INFO_LABEL_TEXT_GAP + textHeight : 0);
            double rowHeight = Math.max(content + 2 * InfographicPainter.BAR_PADDING,
                    InfographicPainter.DISC + 4);
            double h = p.reserve(rowHeight, "infographic-row:" + point.label());
            double y = p.y();
            if (drawShapes) {
                String id = p.nextId();
                p.add(new ImageBox(id, ComponentRole.DECORATION,
                        new Frame(p.x(), y, p.contentWidth(), h), 0, true, null,
                        DECOR_ASSET_PREFIX + "infographic-"
                                + InfographicPainter.encode(section.infographic().shape(), i + 1)
                                + "-" + id,
                        "infographic row"));
            }
            double contentTop = y + Math.max(InfographicPainter.BAR_PADDING, (h - content) / 2);
            addTextAt(p, textX, contentTop, textWidth, labelHeight,
                    ComponentRole.INFOGRAPHIC_LABEL, labelRef, point.label(), link);
            if (textHeight > 0) {
                addTextAt(p, textX, contentTop + labelHeight + INFO_LABEL_TEXT_GAP, textWidth,
                        textHeight, ComponentRole.INFOGRAPHIC_TEXT, textRef, point.text(), link);
            }
            p.advance(h);
            if (i < points.size() - 1) {
                p.advance(INFO_ROW_GAP);
            }
        }
    }

    /**
     * The CARD_GRID shape: two rounded cards per row (painted by
     * {@code InfographicPainter.pointCard}, each with its own numbered
     * badge), an odd trailing point gets a full-width card. Rows reserve at
     * uniform height (the taller cell of the pair wins), matching
     * {@code layoutStandardAsCards}' card-grid convention.
     */
    private void layoutCardGrid(Paginator p, SectionComposition section, Theme theme, SourceLink link) {
        String labelRef = theme.textStyles().containsKey(INFOGRAPHIC_CARD_LABEL_STYLE)
                ? INFOGRAPHIC_CARD_LABEL_STYLE : "Headline";
        String textRef = theme.textStyles().containsKey(INFOGRAPHIC_CARD_TEXT_STYLE)
                ? INFOGRAPHIC_CARD_TEXT_STYLE : "Body";
        TextStyle labelStyle = styleOf(theme, labelRef);
        TextStyle textStyle = styleOf(theme, textRef);
        List<GeneratedArticle.Point> points = section.points();
        double gutter = theme.spacing().gutterPt();
        double badgeBlock = InfographicPainter.CARD_BADGE + INFO_LABEL_TEXT_GAP;

        for (int i = 0; i < points.size(); i += 2) {
            boolean pair = i + 1 < points.size();
            double cardWidth = pair ? (p.contentWidth() - gutter) / 2 : p.contentWidth();
            double innerWidth = cardWidth - 2 * InfographicPainter.CARD_PADDING;

            double leftContent = badgeBlock + cardPointHeight(points.get(i), labelStyle, textStyle, innerWidth);
            double rowContent = leftContent;
            if (pair) {
                rowContent = Math.max(rowContent,
                        badgeBlock + cardPointHeight(points.get(i + 1), labelStyle, textStyle, innerWidth));
            }
            double cardHeight = p.reserve(rowContent + 2 * InfographicPainter.CARD_PADDING,
                    "infographic-card-row:" + points.get(i).label());
            double y = p.y();
            placeCardPoint(p, points.get(i), i + 1, section, theme, labelRef, labelStyle, textRef, textStyle,
                    p.x(), y, cardWidth, cardHeight, link);
            if (pair) {
                placeCardPoint(p, points.get(i + 1), i + 2, section, theme, labelRef, labelStyle, textRef,
                        textStyle, p.x() + cardWidth + gutter, y, cardWidth, cardHeight, link);
            }
            p.advance(cardHeight);
            if (i + 2 < points.size()) {
                p.advance(ITEM_GAP);
            }
        }
    }

    private double cardPointHeight(GeneratedArticle.Point point, TextStyle labelStyle, TextStyle textStyle,
            double innerWidth) {
        double labelHeight = measurer.heightOf(point.label(), labelStyle, innerWidth);
        if (point.text().isBlank()) {
            return labelHeight;
        }
        return labelHeight + INFO_LABEL_TEXT_GAP + measurer.heightOf(point.text(), textStyle, innerWidth);
    }

    private void placeCardPoint(Paginator p, GeneratedArticle.Point point, int number, SectionComposition section,
            Theme theme, String labelRef, TextStyle labelStyle, String textRef, TextStyle textStyle,
            double x, double y, double width, double height, SourceLink link) {
        String id = p.nextId();
        p.add(new ImageBox(id, ComponentRole.DECORATION, new Frame(x, y, width, height),
                0, true, null,
                DECOR_ASSET_PREFIX + "infographic-"
                        + InfographicPainter.encode(section.infographic().shape(), number) + "-" + id,
                "infographic card"));
        double innerWidth = width - 2 * InfographicPainter.CARD_PADDING;
        double textY = y + InfographicPainter.CARD_PADDING + InfographicPainter.CARD_BADGE + INFO_LABEL_TEXT_GAP;
        double labelHeight = measurer.heightOf(point.label(), labelStyle, innerWidth);
        addTextAt(p, x + InfographicPainter.CARD_PADDING, textY, innerWidth, labelHeight,
                ComponentRole.INFOGRAPHIC_LABEL, labelRef, point.label(), link);
        if (!point.text().isBlank()) {
            double textHeight = measurer.heightOf(point.text(), textStyle, innerWidth);
            addTextAt(p, x + InfographicPainter.CARD_PADDING, textY + labelHeight + INFO_LABEL_TEXT_GAP,
                    innerWidth, textHeight, ComponentRole.INFOGRAPHIC_TEXT, textRef, point.text(), link);
        }
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
