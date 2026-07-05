package com.tcs.contentGenerator.agent.design.layout;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.design.CompositionPlan;
import com.tcs.contentGenerator.agent.design.DesignTemplate;
import com.tcs.contentGenerator.agent.design.SectionComposition;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.DesignMeta;
import com.tcs.contentGenerator.design.Frame;
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
    private static final double ICON_GAP = 6;
    private static final int EVENT_SUMMARY_CHARS = 140;
    private static final TextStyle DEFAULT_STYLE = new TextStyle("SansSerif", 10, "normal", "text", 14);

    private final TextMeasurer measurer = new TextMeasurer();

    public DesignDocument layout(CompositionPlan plan, DesignTemplate template, String issueTitle, String jobId) {
        Theme theme = template.theme();
        Paginator p = new Paginator(theme);

        placeText(p, ComponentRole.ISSUE_TITLE, "IssueTitle", styleOf(theme, "IssueTitle"),
                issueTitle, null, p.contentWidth());
        p.advance(SECTION_GAP);

        List<SectionComposition> sections = plan.sections();
        for (int i = 0; i < sections.size(); i++) {
            SectionComposition section = sections.get(i);
            if (i > 0) {
                placeDivider(p, theme);
                p.advance(PARAGRAPH_GAP);
            }
            placeSectionHeader(p, section, theme);
            p.advance(PARAGRAPH_GAP);
            layoutSection(p, section, theme);
            p.advance(SECTION_GAP);
        }

        return new DesignDocument(1, 1, new DesignMeta(issueTitle, jobId), theme, List.of(), p.finish());
    }

    private void layoutSection(Paginator p, SectionComposition section, Theme theme) {
        switch (section.pattern()) {
            case HERO -> layoutHero(p, section, theme);
            case STAT_CALLOUT -> layoutStatCallout(p, section, theme);
            case EVENT_LIST -> layoutEventList(p, section, theme);
            case TWO_COLUMN -> layoutTwoColumn(p, section, theme);
            case STANDARD -> layoutStandard(p, section, theme);
        }
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

    private void layoutStatCallout(Paginator p, SectionComposition section, Theme theme) {
        if (section.articles().isEmpty()) {
            return;
        }
        GeneratedArticle article = section.articles().get(0);
        SourceLink link = new SourceLink(section.section().title(), article.headline());

        placeText(p, ComponentRole.ARTICLE_HEADLINE, "Headline", styleOf(theme, "Headline"),
                article.headline(), link, p.contentWidth());
        p.advance(PARAGRAPH_GAP);

        TextStyle statStyle = styleOf(theme, "Stat");
        TextStyle labelStyle = styleOf(theme, "StatLabel");
        double statWidth = p.contentWidth() * 0.35;
        double labelWidth = p.contentWidth() - statWidth;
        double statHeight = measurer.heightOf(section.statValue(), statStyle, statWidth);
        double labelHeight = measurer.heightOf(section.statLabel(), labelStyle, labelWidth);
        double rowHeight = p.reserve(Math.max(statHeight, labelHeight), "stat-callout:" + article.headline());
        double y = p.y();
        addTextAt(p, p.x(), y, statWidth, rowHeight, ComponentRole.STAT_VALUE, "Stat", section.statValue(), link);
        addTextAt(p, p.x() + statWidth, y, labelWidth, rowHeight, ComponentRole.STAT_LABEL, "StatLabel",
                section.statLabel(), link);
        p.advance(rowHeight);
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

    private void placeSectionHeader(Paginator p, SectionComposition section, Theme theme) {
        TextStyle style = styleOf(theme, "SectionTitle");
        double textWidth = p.contentWidth() - ICON_SIZE - ICON_GAP;
        double textHeight = measurer.heightOf(section.section().title(), style, textWidth);
        double h = p.reserve(Math.max(textHeight, ICON_SIZE), "section-title:" + section.section().title());
        double y = p.y();
        p.add(new ShapeBox(p.nextId(), ComponentRole.SECTION_ICON, new Frame(p.x(), y, ICON_SIZE, ICON_SIZE),
                0, false, null, "circle", section.iconColorRole()));
        p.add(new TextBox(p.nextId(), ComponentRole.SECTION_TITLE,
                new Frame(p.x() + ICON_SIZE + ICON_GAP, y, textWidth, h), 0, false, null,
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
