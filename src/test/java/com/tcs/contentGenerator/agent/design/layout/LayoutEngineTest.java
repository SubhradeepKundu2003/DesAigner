package com.tcs.contentGenerator.agent.design.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tcs.contentGenerator.agent.design.CompositionPlan;
import com.tcs.contentGenerator.agent.design.DesignTemplate;
import com.tcs.contentGenerator.agent.design.SectionComposition;
import com.tcs.contentGenerator.agent.design.SectionPattern;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.design.Component;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.PageSize;
import com.tcs.contentGenerator.design.Spacing;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;

/**
 * Layout invariants that must hold for every pattern the composition agent can
 * pick, regardless of content — deterministic, no LLM needed. A deliberately
 * small page size makes pagination fire across a handful of sections without
 * needing paragraphs long enough to overflow any single component (which
 * would trigger the accepted-tradeoff clamp path and is out of scope here).
 */
class LayoutEngineTest {

    private static final double MARGIN = 10;
    private static final double PAGE_WIDTH = 300;
    private static final double PAGE_HEIGHT = 220;
    private static final double EPSILON = 0.01;

    private static DesignTemplate fixtureTemplate() {
        Theme theme = new Theme(
                new PageSize(PAGE_WIDTH, PAGE_HEIGHT),
                Map.of("background", "#fff", "text", "#000", "primary", "#000",
                        "secondary", "#000", "accent", "#000", "muted", "#000", "divider", "#ccc"),
                Map.of(
                        "IssueTitle", new TextStyle("SansSerif", 16, "bold", "text", 20),
                        "SectionTitle", new TextStyle("SansSerif", 12, "bold", "text", 16),
                        "HeroHeadline", new TextStyle("SansSerif", 12, "bold", "text", 16),
                        "Headline", new TextStyle("SansSerif", 10, "bold", "text", 14),
                        "Body", new TextStyle("SansSerif", 9, "normal", "text", 12),
                        "Stat", new TextStyle("SansSerif", 18, "bold", "text", 22),
                        "StatLabel", new TextStyle("SansSerif", 8, "normal", "text", 10),
                        "EventItem", new TextStyle("SansSerif", 9, "normal", "text", 12)),
                new Spacing(MARGIN, 8));
        return new DesignTemplate("test", theme);
    }

    private static CompositionPlan fixturePlan() {
        GeneratedArticle hero = new GeneratedArticle("Leadership headline",
                "A short leadership message body with a couple of sentences of prose.", null);
        SectionComposition heroSection = new SectionComposition(
                NewsletterSection.LEADERSHIP_MESSAGE, SectionPattern.HERO, List.of(hero), "primary", null,
                null, null);

        GeneratedArticle stat = new GeneratedArticle("Delivery headline",
                "Customer satisfaction keeps climbing this quarter.", null);
        SectionComposition statSection = new SectionComposition(
                NewsletterSection.DELIVERY_HIGHLIGHTS, SectionPattern.STAT_CALLOUT, List.of(stat),
                "primary", null, "72", "NPS score");

        GeneratedArticle standardA = new GeneratedArticle("Project headline one",
                "The first project update body describing recent progress in a few sentences.", null);
        GeneratedArticle standardB = new GeneratedArticle("Project headline two",
                "The second project update body describing a different milestone this month.", null);
        SectionComposition standardSection = new SectionComposition(
                NewsletterSection.PROJECT_UPDATES, SectionPattern.STANDARD,
                List.of(standardA, standardB), "secondary", null, null, null);

        GeneratedArticle colA = new GeneratedArticle("Customer story one",
                "A short customer success story body for the first column.", null);
        GeneratedArticle colB = new GeneratedArticle("Customer story two",
                "A short customer success story body for the second column.", null);
        SectionComposition twoColumnSection = new SectionComposition(
                NewsletterSection.CUSTOMER_SUCCESS, SectionPattern.TWO_COLUMN,
                List.of(colA, colB), "secondary", null, null, null);

        GeneratedArticle eventA = new GeneratedArticle("Town hall",
                "Join the quarterly town hall next week.", null);
        GeneratedArticle eventB = new GeneratedArticle("Training day",
                "A hands-on training day for the whole team.", null);
        SectionComposition eventSection = new SectionComposition(
                NewsletterSection.UPCOMING_EVENTS, SectionPattern.EVENT_LIST,
                List.of(eventA, eventB), "secondary", null, null, null);

        return new CompositionPlan("test",
                List.of(heroSection, statSection, standardSection, twoColumnSection, eventSection));
    }

    @Test
    void paginatesAcrossMultiplePages() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), fixtureTemplate(), "Test Issue", "job-1");
        assertTrue(document.pages().size() >= 2,
                "expected the small test page to force pagination, got " + document.pages().size());
    }

    @Test
    void framesStayWithinPageMargins() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), fixtureTemplate(), "Test Issue", "job-1");
        double maxX = PAGE_WIDTH - MARGIN;
        double maxY = PAGE_HEIGHT - MARGIN;
        for (Page page : document.pages()) {
            for (Component c : page.components()) {
                Frame f = c.frame();
                assertTrue(f.x() >= MARGIN - EPSILON, page.id() + " " + c.id() + " x within left margin");
                assertTrue(f.y() >= MARGIN - EPSILON, page.id() + " " + c.id() + " y within top margin");
                assertTrue(f.x() + f.w() <= maxX + EPSILON, page.id() + " " + c.id() + " within right margin");
                assertTrue(f.y() + f.h() <= maxY + EPSILON, page.id() + " " + c.id() + " within bottom margin");
            }
        }
    }

    @Test
    void componentsOnTheSamePageDoNotOverlap() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), fixtureTemplate(), "Test Issue", "job-1");
        for (Page page : document.pages()) {
            List<Component> components = page.components();
            for (int i = 0; i < components.size(); i++) {
                for (int j = i + 1; j < components.size(); j++) {
                    Frame a = components.get(i).frame();
                    Frame b = components.get(j).frame();
                    assertTrue(!overlaps(a, b),
                            page.id() + ": " + components.get(i).id() + " overlaps " + components.get(j).id());
                }
            }
        }
    }

    @Test
    void everyArticleIsPlacedSomewhere() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), fixtureTemplate(), "Test Issue", "job-1");
        List<String> allText = document.pages().stream()
                .flatMap(p -> p.components().stream())
                .filter(TextBox.class::isInstance)
                .map(c -> ((TextBox) c).text())
                .toList();

        for (SectionComposition section : fixturePlan().sections()) {
            for (GeneratedArticle article : section.articles()) {
                boolean found = allText.stream().anyMatch(text -> text.contains(article.headline()));
                assertTrue(found, "expected to find headline \"" + article.headline() + "\" somewhere in the design");
            }
        }
    }

    @Test
    void mastheadPlacesLogoBesideIssueTitleWithoutOverlap() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), fixtureTemplate(), "Test Issue", "job-1");
        List<Component> firstPage = document.pages().get(0).components();

        ImageBox logo = firstPage.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(box -> box.role() == ComponentRole.LOGO)
                .findFirst().orElseThrow(() -> new AssertionError("expected a LOGO ImageBox on the first page"));
        TextBox title = firstPage.stream()
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(box -> box.role() == ComponentRole.ISSUE_TITLE)
                .findFirst().orElseThrow(() -> new AssertionError("expected an ISSUE_TITLE TextBox on the first page"));

        assertTrue(!overlaps(logo.frame(), title.frame()), "logo and issue title must not overlap");
        assertTrue(Math.abs(logo.frame().y() - MARGIN) < EPSILON, "logo should start at the top margin");
        assertTrue(Math.abs(title.frame().y() - MARGIN) < EPSILON, "issue title should start at the top margin");
        assertTrue(logo.frame().x() < title.frame().x(), "logo should sit to the left of the issue title");
    }

    @Test
    void sectionWithIconAssetGetsAnImageBoxIconInsteadOfTheDot() {
        GeneratedArticle article = new GeneratedArticle("Delivery headline",
                "A short body.", null);
        SectionComposition section = new SectionComposition(
                NewsletterSection.DELIVERY_HIGHLIGHTS, SectionPattern.STANDARD, List.of(article),
                "primary", "icon-DELIVERY_HIGHLIGHTS", null, null);
        DesignDocument document = new LayoutEngine().layout(
                new CompositionPlan("test", List.of(section)), fixtureTemplate(), "Test Issue", "job-1");

        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        ImageBox icon = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(box -> box.role() == ComponentRole.SECTION_ICON)
                .findFirst().orElseThrow(() -> new AssertionError("expected a SECTION_ICON ImageBox"));
        assertTrue("icon-DELIVERY_HIGHLIGHTS".equals(icon.assetId()),
                "icon ImageBox should reference the section's icon asset id");
        assertTrue(all.stream().noneMatch(c -> c.role() == ComponentRole.SECTION_ICON && !(c instanceof ImageBox)),
                "no dot ShapeBox expected when the section has an icon asset");
    }

    // ---- decor layer -----------------------------------------------------

    private static DesignTemplate decorTemplate() {
        DesignTemplate plain = fixtureTemplate();
        return new DesignTemplate(plain.name(), plain.theme(), new com.tcs.contentGenerator.agent.design.Decor(
                new com.tcs.contentGenerator.agent.design.Decor.Masthead("gradient-band", "primary", "secondary", 0, 60, "wave"),
                new com.tcs.contentGenerator.agent.design.Decor.SectionHeader("chip", "primary"),
                new com.tcs.contentGenerator.agent.design.Decor.Photo("rounded", 12, true),
                new com.tcs.contentGenerator.agent.design.Decor.StatCard("background", "primary", true),
                new com.tcs.contentGenerator.agent.design.Decor.Footer("band", "primary", "secondary")));
    }

    @Test
    void mastheadBandIsFirstFullWidthAndBehindTheTitle() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), decorTemplate(), "Test Issue", "job-1");
        List<Component> firstPage = document.pages().get(0).components();

        Component band = firstPage.get(0);
        assertTrue(band instanceof ImageBox && band.role() == ComponentRole.DECORATION,
                "the masthead band must be the first component so it paints behind everything");
        assertTrue(((ImageBox) band).assetId().startsWith("decor-masthead-"), "band asset id");
        assertTrue(Math.abs(band.frame().x()) < EPSILON && Math.abs(band.frame().w() - PAGE_WIDTH) < EPSILON,
                "band must be full-bleed");
        TextBox title = firstPage.stream()
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(box -> box.role() == ComponentRole.ISSUE_TITLE)
                .findFirst().orElseThrow();
        assertTrue(band.frame().h() >= title.frame().y() + title.frame().h(),
                "band must be tall enough to contain the title");
    }

    @Test
    void contentAfterTheMastheadStartsBelowTheBand() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), decorTemplate(), "Test Issue", "job-1");
        List<Component> firstPage = document.pages().get(0).components();
        double bandBottom = firstPage.get(0).frame().h();
        firstPage.stream()
                .filter(c -> c.role() != ComponentRole.DECORATION && c.role() != ComponentRole.LOGO
                        && c.role() != ComponentRole.ISSUE_TITLE)
                .forEach(c -> assertTrue(c.frame().y() >= bandBottom - EPSILON,
                        c.id() + " (" + c.role() + ") must start below the masthead band"));
    }

    @Test
    void everyPageEndsWithAFooterBand() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), decorTemplate(), "Test Issue", "job-1");
        for (Page page : document.pages()) {
            Component last = page.components().get(page.components().size() - 1);
            assertTrue(last instanceof ImageBox box && box.assetId().startsWith("decor-footer-"),
                    page.id() + " must end with the footer band");
            assertTrue(Math.abs(last.frame().y() + last.frame().h() - PAGE_HEIGHT) < EPSILON,
                    "footer must sit flush with the page bottom");
        }
    }

    @Test
    void statCardSitsBehindTheStatRow() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), decorTemplate(), "Test Issue", "job-1");
        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        ImageBox card = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(box -> box.assetId() != null && box.assetId().startsWith("decor-statcard-"))
                .findFirst().orElseThrow(() -> new AssertionError("expected a stat card decoration"));
        Component statValue = all.stream().filter(c -> c.role() == ComponentRole.STAT_VALUE)
                .findFirst().orElseThrow();
        Frame c = card.frame();
        Frame s = statValue.frame();
        assertTrue(s.x() >= c.x() && s.y() >= c.y()
                        && s.x() + s.w() <= c.x() + c.w() + EPSILON
                        && s.y() + s.h() <= c.y() + c.h() + EPSILON,
                "the stat value must sit inside the card");
    }

    @Test
    void chipEnclosesTheSectionIconDot() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), decorTemplate(), "Test Issue", "job-1");
        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        ImageBox chip = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(box -> box.assetId() != null && box.assetId().startsWith("decor-chip-"))
                .findFirst().orElseThrow(() -> new AssertionError("expected a section chip decoration"));
        Component icon = all.stream().filter(c -> c.role() == ComponentRole.SECTION_ICON
                        && c.frame().y() >= chip.frame().y() - EPSILON
                        && c.frame().y() <= chip.frame().y() + chip.frame().h())
                .findFirst().orElseThrow(() -> new AssertionError("expected an icon within the chip's row"));
        assertTrue(icon.frame().x() >= chip.frame().x() - EPSILON
                        && icon.frame().x() + icon.frame().w() <= chip.frame().x() + chip.frame().w() + EPSILON,
                "the icon must be horizontally centered inside the chip");
    }

    @Test
    void decorlessTemplateProducesNoDecorationComponents() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), fixtureTemplate(), "Test Issue", "job-1");
        assertTrue(document.pages().stream().flatMap(p -> p.components().stream())
                        .noneMatch(c -> c.role() == ComponentRole.DECORATION),
                "a template without decor must lay out exactly as before");
    }

    private static boolean overlaps(Frame a, Frame b) {
        boolean separated = a.x() + a.w() <= b.x() + EPSILON
                || b.x() + b.w() <= a.x() + EPSILON
                || a.y() + a.h() <= b.y() + EPSILON
                || b.y() + b.h() <= a.y() + EPSILON;
        return !separated;
    }
}
