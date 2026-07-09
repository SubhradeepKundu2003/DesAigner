package com.tcs.contentGenerator.agent.design.layout;

import java.util.List;

import org.springframework.stereotype.Component;

import java.util.ArrayList;

import com.tcs.contentGenerator.agent.design.CompositionPlan;
import com.tcs.contentGenerator.agent.design.Decor;
import com.tcs.contentGenerator.agent.design.DesignTemplate;
import com.tcs.contentGenerator.agent.design.SectionComposition;
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

    private final TextMeasurer measurer = new TextMeasurer();

    public DesignDocument layout(CompositionPlan plan, DesignTemplate template, String issueTitle, String jobId) {
        Theme theme = template.theme();
        Decor decor = template.decor();
        Paginator p = new Paginator(theme);

        placeMasthead(p, theme, decor, issueTitle);
        p.advance(SECTION_GAP);

        List<SectionComposition> sections = plan.sections();
        for (int i = 0; i < sections.size(); i++) {
            SectionComposition section = sections.get(i);
            if (i > 0) {
                placeDivider(p, theme);
                p.advance(PARAGRAPH_GAP);
            }
            placeSectionHeader(p, section, theme, decor);
            p.advance(PARAGRAPH_GAP);
            layoutSection(p, section, theme, decor);
            p.advance(SECTION_GAP);
        }

        List<Page> pages = p.finish();
        if (decor != null && decor.footer() != null) {
            pages = withFooters(pages, p, theme);
        }
        return new DesignDocument(1, 1, new DesignMeta(issueTitle, jobId), theme, List.of(), pages);
    }

    private void layoutSection(Paginator p, SectionComposition section, Theme theme, Decor decor) {
        switch (section.pattern()) {
            case HERO -> layoutHero(p, section, theme);
            case STAT_CALLOUT -> layoutStatCallout(p, section, theme, decor);
            case EVENT_LIST -> layoutEventList(p, section, theme);
            case TWO_COLUMN -> layoutTwoColumn(p, section, theme);
            case STANDARD -> layoutStandard(p, section, theme);
        }
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
        TextStyle bodyStyle = styleOf(theme, "Body");
        List<GeneratedArticle> articles = section.articles();
        for (int i = 0; i < articles.size(); i++) {
            GeneratedArticle article = articles.get(i);
            SourceLink link = new SourceLink(section.section().title(), article.headline());
            placeText(p, ComponentRole.ARTICLE_HEADLINE, "Headline", headStyle,
                    article.headline(), link, p.contentWidth());
            p.advance(PARAGRAPH_GAP);
            placeText(p, ComponentRole.ARTICLE_BODY, "Body", bodyStyle,
                    article.body(), link, p.contentWidth());
            if (i < articles.size() - 1) {
                p.advance(ITEM_GAP);
            }
        }
    }

    private void layoutHero(Paginator p, SectionComposition section, Theme theme) {
        if (section.articles().isEmpty()) {
            return;
        }
        GeneratedArticle article = section.articles().get(0);
        SourceLink link = new SourceLink(section.section().title(), article.headline());
        placeText(p, ComponentRole.ARTICLE_HEADLINE, "HeroHeadline", styleOf(theme, "HeroHeadline"),
                article.headline(), link, p.contentWidth());
        p.advance(PARAGRAPH_GAP);
        placeText(p, ComponentRole.ARTICLE_BODY, "Body", styleOf(theme, "Body"),
                article.body(), link, p.contentWidth());
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

        placeText(p, ComponentRole.ARTICLE_BODY, "Body", styleOf(theme, "Body"),
                article.body(), link, p.contentWidth());
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
        TextStyle style = styleOf(theme, "SectionTitle");
        boolean hasIconAsset = section.iconAssetId() != null;
        boolean chip = decor != null && decor.sectionHeader() != null
                && "chip".equals(decor.sectionHeader().style());
        double iconSize = hasIconAsset ? ICON_IMAGE_SIZE : ICON_SIZE;
        double leadSize = chip ? CHIP_SIZE : iconSize;
        double textWidth = p.contentWidth() - leadSize - ICON_GAP;
        double textHeight = measurer.heightOf(section.section().title(), style, textWidth);
        double h = p.reserve(Math.max(textHeight, leadSize), "section-title:" + section.section().title());
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
                "SectionTitle", section.section().title()));
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
