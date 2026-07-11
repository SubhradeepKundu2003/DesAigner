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
        return new DesignTemplate(plain.name(), plain.theme(), new com.tcs.contentGenerator.agent.design.Decor(null,
                new com.tcs.contentGenerator.agent.design.Decor.Masthead("gradient-band", "primary", "secondary", 0, 60, "wave"),
                new com.tcs.contentGenerator.agent.design.Decor.SectionHeader("chip", "primary", false),
                null, null,
                new com.tcs.contentGenerator.agent.design.Decor.Photo("rounded", 12, true, null), null,
                new com.tcs.contentGenerator.agent.design.Decor.StatCard("background", "primary", true),
                new com.tcs.contentGenerator.agent.design.Decor.Footer("band", "primary", "secondary")));
    }

    /** Decor with every editorial-polish feature on, plus the optional editorial text styles. */
    private static DesignTemplate editorialTemplate() {
        DesignTemplate plain = fixtureTemplate();
        java.util.Map<String, TextStyle> styles = new java.util.HashMap<>(plain.theme().textStyles());
        styles.put("BodyLead", new TextStyle("SansSerif", 10.5, "normal", "muted", 13));
        styles.put("SectionTitleKicker", new TextStyle("SansSerif", 11, "bold", "primary", 15));
        Theme theme = new Theme(plain.theme().pageSize(),
                new java.util.HashMap<>(plain.theme().colors()) {{ put("surface", "#eeeeee"); }},
                styles, plain.theme().spacing());
        return new DesignTemplate(plain.name(), theme, new com.tcs.contentGenerator.agent.design.Decor(null,
                new com.tcs.contentGenerator.agent.design.Decor.Masthead("gradient-band", "primary", "secondary", 0, 60, "flat"),
                new com.tcs.contentGenerator.agent.design.Decor.SectionHeader("chip", "primary", true),
                new com.tcs.contentGenerator.agent.design.Decor.Hero("panel", "surface", "primary"),
                new com.tcs.contentGenerator.agent.design.Decor.SectionBand("surface"),
                new com.tcs.contentGenerator.agent.design.Decor.Photo("rounded", 12, true, null), null,
                new com.tcs.contentGenerator.agent.design.Decor.StatCard("surface", "primary", true),
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
    void photoLedHeroPlacesAFullWidthPhotoSlotAboveTheHeadline() {
        DesignTemplate base = editorialTemplate();
        var decor = base.decor();
        DesignTemplate photoLed = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 800), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                new com.tcs.contentGenerator.agent.design.Decor(null, decor.masthead(), decor.sectionHeader(),
                        new com.tcs.contentGenerator.agent.design.Decor.Hero("photo-led", "surface", "primary"),
                        decor.sectionBand(), decor.photo(), decor.cards(), decor.statCard(), decor.footer()));
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), photoLed, "Test Issue", "job-1");
        List<Component> firstPage = document.pages().get(0).components();

        ImageBox heroPhoto = firstPage.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(box -> box.role() == ComponentRole.IMAGE_PLACEHOLDER && box.assetId() == null)
                .findFirst().orElseThrow(() -> new AssertionError("expected a hero photo slot"));
        assertTrue(Math.abs(heroPhoto.frame().w() - (PAGE_WIDTH - 2 * MARGIN)) < EPSILON,
                "hero photo is full content width");
        TextBox heroHeadline = firstPage.stream()
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(box -> "HeroHeadline".equals(box.styleRef()))
                .findFirst().orElseThrow();
        assertTrue(heroHeadline.frame().y() >= heroPhoto.frame().y() + heroPhoto.frame().h() - EPSILON,
                "the headline sits below the hero photo");
        assertTrue(firstPage.stream().noneMatch(c -> c instanceof ImageBox box
                        && box.assetId() != null && box.assetId().startsWith("decor-heropanel-")),
                "photo-led hero has no panel");
    }

    @Test
    void heroPanelWrapsTheHeroHeadlineAndBody() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), editorialTemplate(), "Test Issue", "job-1");
        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        ImageBox panel = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(box -> box.assetId() != null && box.assetId().startsWith("decor-heropanel-"))
                .findFirst().orElseThrow(() -> new AssertionError("expected a hero panel decoration"));
        TextBox heroHeadline = all.stream()
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(box -> "HeroHeadline".equals(box.styleRef()))
                .findFirst().orElseThrow();
        Frame panelFrame = panel.frame();
        Frame headFrame = heroHeadline.frame();
        assertTrue(headFrame.x() >= panelFrame.x() && headFrame.y() >= panelFrame.y()
                        && headFrame.x() + headFrame.w() <= panelFrame.x() + panelFrame.w() + EPSILON
                        && headFrame.y() + headFrame.h() <= panelFrame.y() + panelFrame.h() + EPSILON,
                "the hero headline must sit inside the panel");
    }

    @Test
    void everyOtherSectionGetsAFullBleedTintBand() {
        // a taller page than the pagination fixture: bands are skipped for
        // page-crossing sections, and photo slots make sections tall
        DesignTemplate base = editorialTemplate();
        DesignTemplate tallPage = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 800), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                base.decor());
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), tallPage, "Test Issue", "job-1");
        List<com.tcs.contentGenerator.design.ShapeBox> bands = document.pages().stream()
                .flatMap(p -> p.components().stream())
                .filter(c -> c.role() == ComponentRole.DECORATION)
                .filter(com.tcs.contentGenerator.design.ShapeBox.class::isInstance)
                .map(com.tcs.contentGenerator.design.ShapeBox.class::cast)
                .toList();
        assertTrue(!bands.isEmpty(), "expected at least one section tint band (odd sections, same-page only)");
        for (var band : bands) {
            assertTrue(Math.abs(band.frame().x()) < EPSILON
                            && Math.abs(band.frame().w() - PAGE_WIDTH) < EPSILON,
                    "tint bands must be full-bleed");
            assertTrue("surface".equals(band.fillColorRole()), "band uses the sectionBand fill role");
        }
    }

    @Test
    void kickerHeadersUppercaseTheTitleAndDropTheDividers() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), editorialTemplate(), "Test Issue", "job-1");
        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        TextBox sectionTitle = all.stream()
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(box -> box.role() == ComponentRole.SECTION_TITLE)
                .findFirst().orElseThrow();
        assertTrue(sectionTitle.text().equals(sectionTitle.text().toUpperCase(java.util.Locale.ROOT)),
                "kicker titles are uppercase: " + sectionTitle.text());
        assertTrue("SectionTitleKicker".equals(sectionTitle.styleRef()));
        // accent bars exist (small DIVIDER-role rects), full-width divider rules do not
        assertTrue(all.stream().anyMatch(c -> c.role() == ComponentRole.DIVIDER
                        && c.frame().w() < 40), "expected kicker accent bars");
        assertTrue(all.stream().noneMatch(c -> c.role() == ComponentRole.DIVIDER
                        && c.frame().w() > 100), "full-width dividers must be gone in kicker mode");
    }

    @Test
    void leadParagraphIsSplitOffWhenThemeDefinesBodyLead() {
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), editorialTemplate(), "Test Issue", "job-1");
        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        // the fixture's standard-section bodies are single-paragraph, so use the hero:
        // its body is one paragraph too — craft a two-paragraph article instead
        GeneratedArticle article = new GeneratedArticle("Two paragraph article",
                "The first paragraph acts as the lead.\n\nThe second paragraph is the body.", null);
        SectionComposition section = new SectionComposition(NewsletterSection.PROJECT_UPDATES,
                SectionPattern.STANDARD, List.of(article), "primary", null, null, null);
        DesignDocument doc2 = new LayoutEngine().layout(
                new CompositionPlan("test", List.of(section)), editorialTemplate(), "Test Issue", "job-1");
        List<Component> components = doc2.pages().stream().flatMap(p -> p.components().stream()).toList();
        TextBox lead = components.stream()
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(box -> box.role() == ComponentRole.ARTICLE_LEAD)
                .findFirst().orElseThrow(() -> new AssertionError("expected an ARTICLE_LEAD box"));
        assertTrue("BodyLead".equals(lead.styleRef()));
        assertTrue(lead.text().contains("first paragraph"));
        TextBox rest = components.stream()
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(box -> box.role() == ComponentRole.ARTICLE_BODY)
                .findFirst().orElseThrow();
        assertTrue(rest.text().contains("second paragraph") && !rest.text().contains("first paragraph"));
        // single-paragraph bodies in the same run stay unsplit
        assertTrue(all.stream().filter(c -> c.role() == ComponentRole.ARTICLE_LEAD).count()
                        <= all.stream().filter(c -> c.role() == ComponentRole.ARTICLE_BODY).count(),
                "never more leads than bodies");
    }

    @Test
    void photoDecorReservesAnEmptySlotPerEligibleSection() {
        // needs a page tall enough for ALL slots to fit: a slot that would
        // spill onto a fresh page is skipped (no orphan image-only pages)
        DesignTemplate base = editorialTemplate();
        DesignTemplate tallPage = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 1600), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                base.decor());
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), tallPage, "Test Issue", "job-1");
        List<ImageBox> slots = document.pages().stream().flatMap(p -> p.components().stream())
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(box -> box.role() == ComponentRole.IMAGE_PLACEHOLDER && box.assetId() == null)
                .toList();
        // fixture plan: hero + stat + standard + two-column + events → slots for
        // stat, standard, two-column (hero and event list are excluded)
        assertTrue(slots.size() == 3, "expected 3 reserved photo slots, got " + slots.size());
        for (ImageBox slot : slots) {
            assertTrue(slot.source() != null, "slots carry the article link for image resolution");
        }
        // and the decor-less template reserves none
        DesignDocument plain = new LayoutEngine().layout(fixturePlan(), fixtureTemplate(), "Test Issue", "job-1");
        assertTrue(plain.pages().stream().flatMap(p -> p.components().stream())
                .noneMatch(c -> c instanceof ImageBox box && box.assetId() == null
                        && box.role() == ComponentRole.IMAGE_PLACEHOLDER));
    }

    @Test
    void sideImagePlacementAlternatesAcrossArticles() {
        DesignTemplate base = editorialTemplate();
        var d = base.decor();
        DesignTemplate sideImages = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 1600), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                new com.tcs.contentGenerator.agent.design.Decor(null, d.masthead(), d.sectionHeader(), d.hero(),
                        d.sectionBand(),
                        new com.tcs.contentGenerator.agent.design.Decor.Photo("rounded", 12, true, "side"), null,
                        d.statCard(), d.footer()));
        GeneratedArticle first = new GeneratedArticle("First side article",
                "A body long enough to sit beside its image comfortably.", null);
        GeneratedArticle second = new GeneratedArticle("Second side article",
                "Another body long enough to sit beside its image comfortably.", null);
        SectionComposition section = new SectionComposition(NewsletterSection.PROJECT_UPDATES,
                SectionPattern.STANDARD, List.of(first, second), "primary", null, null, null);
        DesignDocument document = new LayoutEngine().layout(
                new CompositionPlan("test", List.of(section)), sideImages, "Test Issue", "job-1");

        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        List<ImageBox> slots = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(box -> box.role() == ComponentRole.IMAGE_PLACEHOLDER && box.assetId() == null)
                .toList();
        assertTrue(slots.size() == 2, "one side slot per article, got " + slots.size());
        ImageBox slotA = slots.get(0);
        ImageBox slotB = slots.get(1);
        assertTrue(slotA.frame().x() != slotB.frame().x(),
                "consecutive side images must sit on opposite sides");
        // each slot's article text sits beside (not below) it: the matching
        // headline shares the slot's row
        for (ImageBox slot : slots) {
            TextBox headline = all.stream()
                    .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                    .filter(b -> b.role() == ComponentRole.ARTICLE_HEADLINE
                            && b.text().equals(slot.altText()))
                    .findFirst().orElseThrow();
            assertTrue(Math.abs(headline.frame().y() - slot.frame().y()) < EPSILON,
                    "headline and image share the row top");
            assertTrue(!overlaps(headline.frame(), slot.frame()), "text and image must not overlap");
        }
        // no extra below-section slot for a side-image section
        assertTrue(slots.size() == 2, "no additional below-slot expected");
    }

    @Test
    void coverDecorProducesADedicatedFirstPage() {
        DesignTemplate base = editorialTemplate();
        var d = base.decor();
        java.util.Map<String, TextStyle> styles = new java.util.HashMap<>(base.theme().textStyles());
        styles.put("CoverTitle", new TextStyle("SansSerif", 20, "normal", "background", 24, "right"));
        styles.put("CoverTitleAccent", new TextStyle("SansSerif", 24, "bold", "primary", 28, "right"));
        styles.put("CoverSubtitle", new TextStyle("SansSerif", 8, "normal", "background", 11, "right"));
        DesignTemplate covered = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 800), base.theme().colors(), styles,
                        base.theme().spacing()),
                new com.tcs.contentGenerator.agent.design.Decor(
                        new com.tcs.contentGenerator.agent.design.Decor.Cover("text", "primary"),
                        d.masthead(), d.sectionHeader(), d.hero(), d.sectionBand(),
                        d.photo(), d.cards(), d.statCard(), d.footer()));
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), covered,
                "TD Monthly Newsletter — July 2026", "job-1");

        List<Component> cover = document.pages().get(0).components();
        // full-bleed background first, cover logo, empty photo slot, title split, wave band
        assertTrue(cover.get(0) instanceof com.tcs.contentGenerator.design.ShapeBox shape
                        && shape.role() == ComponentRole.DECORATION
                        && Math.abs(shape.frame().h() - 800) < EPSILON,
                "cover starts with a full-bleed background");
        assertTrue(cover.stream().anyMatch(c -> c instanceof ImageBox box
                        && LayoutEngine.COVER_LOGO_ASSET_ID.equals(box.assetId())),
                "cover carries its own logo variant");
        assertTrue(cover.stream().anyMatch(c -> c instanceof ImageBox box
                        && box.assetId() == null && box.role() == ComponentRole.DECORATION),
                "cover has an empty photo slot for the graphics agent");
        List<String> titles = cover.stream()
                .filter(TextBox.class::isInstance).map(TextBox.class::cast)
                .filter(b -> b.role() == ComponentRole.ISSUE_TITLE)
                .map(TextBox::text).toList();
        assertTrue(titles.contains("TD Monthly") && titles.contains("NEWSLETTER")
                        && titles.contains("July 2026"),
                "issue title splits into display lines, got " + titles);
        assertTrue(cover.stream().anyMatch(c -> c instanceof ImageBox box
                        && box.assetId() != null && box.assetId().startsWith("decor-coverwaves-")),
                "cover ends with the wave band");
        // masthead content starts on page 2
        assertTrue(document.pages().size() >= 2);
        assertTrue(document.pages().get(1).components().stream()
                        .anyMatch(c -> c.role() == ComponentRole.LOGO),
                "the regular masthead moved to page 2");
    }

    @Test
    void threePlusArticlesLayOutAsACardGrid() {
        DesignTemplate base = editorialTemplate();
        var d = base.decor();
        DesignTemplate carded = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 1600), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                new com.tcs.contentGenerator.agent.design.Decor(null, d.masthead(), d.sectionHeader(),
                        d.hero(), d.sectionBand(), d.photo(),
                        new com.tcs.contentGenerator.agent.design.Decor.Cards("surface"),
                        d.statCard(), d.footer()));
        List<GeneratedArticle> articles = List.of(
                new GeneratedArticle("Card one", "First card body.", null),
                new GeneratedArticle("Card two", "Second card body with a little more text.", null),
                new GeneratedArticle("Card three", "Third card body.", null));
        SectionComposition section = new SectionComposition(NewsletterSection.PROJECT_UPDATES,
                SectionPattern.STANDARD, articles, "primary", null, null, null);
        DesignDocument document = new LayoutEngine().layout(
                new CompositionPlan("test", List.of(section)), carded, "Test Issue", "job-1");

        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        List<ImageBox> cardBoxes = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(b -> b.assetId() != null && b.assetId().startsWith("decor-card-"))
                .toList();
        assertTrue(cardBoxes.size() == 3, "one card per article, got " + cardBoxes.size());
        // first two cards share a row at half width; the odd third is full width
        assertTrue(Math.abs(cardBoxes.get(0).frame().y() - cardBoxes.get(1).frame().y()) < EPSILON,
                "first two cards share a row");
        assertTrue(Math.abs(cardBoxes.get(0).frame().h() - cardBoxes.get(1).frame().h()) < EPSILON,
                "paired cards have equal heights");
        assertTrue(cardBoxes.get(2).frame().w() > cardBoxes.get(0).frame().w() * 1.5,
                "odd trailing card is full width");
        // headlines sit inside their cards
        for (ImageBox card : cardBoxes) {
            assertTrue(all.stream().anyMatch(c -> c instanceof TextBox t
                            && t.role() == ComponentRole.ARTICLE_HEADLINE
                            && t.frame().x() >= card.frame().x()
                            && t.frame().y() >= card.frame().y()
                            && t.frame().x() + t.frame().w() <= card.frame().x() + card.frame().w() + EPSILON),
                    "expected a headline inside card " + card.id());
        }
        // card sections get no side-image slots and no below-slot
        assertTrue(all.stream().noneMatch(c -> c instanceof ImageBox b
                        && b.assetId() == null && b.role() == ComponentRole.IMAGE_PLACEHOLDER),
                "card sections must not also get photo slots");
    }

    @Test
    void kpiTilesLayOutAsARowOfStatCards() {
        DesignTemplate base = decorTemplate(); // has statCard decor
        DesignTemplate tallPage = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 800), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                base.decor());
        List<SectionComposition.Stat> stats = List.of(
                new SectionComposition.Stat("72", "NPS"),
                new SectionComposition.Stat("18%", "growth"),
                new SectionComposition.Stat("4.6", "rating"));
        GeneratedArticle article = new GeneratedArticle("Delivery headline",
                "A short delivery body with a couple of sentences.", null);
        SectionComposition section = new SectionComposition(NewsletterSection.DELIVERY_HIGHLIGHTS,
                SectionPattern.KPI_TILES, List.of(article), "primary", null, null, null, stats);
        DesignDocument document = new LayoutEngine().layout(
                new CompositionPlan("test", List.of(section)), tallPage, "Test Issue", "job-1");

        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        List<ImageBox> tiles = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(b -> b.assetId() != null && b.assetId().startsWith("decor-statcard-"))
                .toList();
        assertTrue(tiles.size() == 3, "one stat-card per KPI tile, got " + tiles.size());
        // all three tiles share the row (top + uniform height) and march left to right
        for (int i = 1; i < tiles.size(); i++) {
            assertTrue(Math.abs(tiles.get(i).frame().y() - tiles.get(0).frame().y()) < EPSILON,
                    "tiles share the row top");
            assertTrue(Math.abs(tiles.get(i).frame().h() - tiles.get(0).frame().h()) < EPSILON,
                    "tiles share a uniform height");
            assertTrue(tiles.get(i).frame().x() > tiles.get(i - 1).frame().x(),
                    "tiles are laid left to right");
        }
        assertTrue(all.stream().filter(c -> c.role() == ComponentRole.STAT_VALUE).count() == 3
                        && all.stream().filter(c -> c.role() == ComponentRole.STAT_LABEL).count() == 3,
                "three value and three label boxes");
        for (ImageBox tile : tiles) {
            assertTrue(all.stream().anyMatch(c -> c.role() == ComponentRole.STAT_VALUE
                            && c.frame().x() >= tile.frame().x() - EPSILON
                            && c.frame().y() >= tile.frame().y() - EPSILON
                            && c.frame().x() + c.frame().w() <= tile.frame().x() + tile.frame().w() + EPSILON
                            && c.frame().y() + c.frame().h() <= tile.frame().y() + tile.frame().h() + EPSILON),
                    "each tile contains a stat value");
        }
        assertTrue(all.stream().anyMatch(c -> c.role() == ComponentRole.ARTICLE_BODY),
                "the article body still renders below the KPI row");
    }

    @Test
    void perSectionBandTintsEverySectionByItsOwnRole() {
        DesignTemplate base = editorialTemplate();
        var d = base.decor();
        DesignTemplate perSection = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 1600), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                new com.tcs.contentGenerator.agent.design.Decor(null, d.masthead(), d.sectionHeader(), d.hero(),
                        new com.tcs.contentGenerator.agent.design.Decor.SectionBand("surface", "per-section"),
                        d.photo(), d.cards(), d.statCard(), d.footer()));
        DesignDocument document = new LayoutEngine().layout(fixturePlan(), perSection, "Test Issue", "job-1");

        List<ImageBox> tints = document.pages().stream().flatMap(p -> p.components().stream())
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(b -> b.assetId() != null && b.assetId().startsWith("decor-sectiontint-"))
                .toList();
        // 5 fixture sections; page-crossing ones skip their band — most still get one
        assertTrue(tints.size() >= 3, "expected most sections to get a tint band, got " + tints.size());
        for (ImageBox tint : tints) {
            assertTrue(Math.abs(tint.frame().x()) < EPSILON
                            && Math.abs(tint.frame().w() - PAGE_WIDTH) < EPSILON,
                    "section tint must be full-bleed");
        }
        // each tint carries its section's own type color role (decor-sectiontint-<role>-<id>)
        List<String> roles = tints.stream().map(b -> b.assetId().split("-")[2]).distinct().toList();
        assertTrue(roles.contains("primary") && roles.contains("secondary"),
                "sections are tinted by their own type color, got roles " + roles);
        // per-section mode bakes tints as ImageBoxes, not solid surface ShapeBoxes
        assertTrue(document.pages().stream().flatMap(p -> p.components().stream())
                        .noneMatch(c -> c instanceof com.tcs.contentGenerator.design.ShapeBox s
                                && s.role() == ComponentRole.DECORATION && "surface".equals(s.fillColorRole())),
                "no solid surface ShapeBox bands in per-section mode");
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

    @Test
    void infographicLaysOutNumberedRowsWithTextInsideAndBodyBelow() {
        DesignTemplate base = fixtureTemplate();
        DesignTemplate tallPage = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 800), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                base.decor());
        com.tcs.contentGenerator.agent.design.infographic.InfographicSpec spec =
                new com.tcs.contentGenerator.agent.design.infographic.InfographicSpec(
                        "numbered-bars",
                        com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Archetype.NUMBERED_LIST,
                        3, 5, 60, 220, false,
                        com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Background.ANY,
                        new com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Shape(
                                "numberedBars", "primary", "text"));
        List<GeneratedArticle.Point> points = List.of(
                new GeneratedArticle.Point("Discovery", "Requirements signed off."),
                new GeneratedArticle.Point("Build", "Development finished early."),
                new GeneratedArticle.Point("Go-live", "Rollout reached every region."));
        GeneratedArticle article = new GeneratedArticle("Project headline",
                "A short project body with a couple of sentences.", null, points);
        SectionComposition section = new SectionComposition(NewsletterSection.PROJECT_UPDATES,
                SectionPattern.INFOGRAPHIC, List.of(article), "primary", null, null, null,
                List.of(), spec, points);
        DesignDocument document = new LayoutEngine().layout(
                new CompositionPlan("test", List.of(section)), tallPage, "Test Issue", "job-1");

        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        List<ImageBox> rows = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(b -> b.assetId() != null && b.assetId().startsWith("decor-infographic-"))
                .toList();
        assertTrue(rows.size() == 3, "one painted row per point, got " + rows.size());
        // the drawing params ride in the asset id: kind.barRole.discRole.number
        for (int i = 0; i < rows.size(); i++) {
            assertTrue(rows.get(i).assetId().contains("numberedBars.primary.text." + (i + 1)),
                    "row " + i + " id must encode its painter params: " + rows.get(i).assetId());
            if (i > 0) {
                assertTrue(rows.get(i).frame().y() > rows.get(i - 1).frame().y() + EPSILON,
                        "rows stack downward");
            }
        }
        List<Component> labels = all.stream()
                .filter(c -> c.role() == ComponentRole.INFOGRAPHIC_LABEL).toList();
        List<Component> texts = all.stream()
                .filter(c -> c.role() == ComponentRole.INFOGRAPHIC_TEXT).toList();
        assertTrue(labels.size() == 3 && texts.size() == 3, "label + one-liner per point");
        for (int i = 0; i < 3; i++) {
            Frame row = rows.get(i).frame();
            Frame label = labels.get(i).frame();
            assertTrue(label.y() >= row.y() - EPSILON
                            && label.y() + label.h() <= row.y() + row.h() + EPSILON,
                    "each label sits inside its row bar");
        }
        Component body = all.stream().filter(c -> c.role() == ComponentRole.ARTICLE_BODY)
                .findFirst().orElseThrow();
        Frame lastRow = rows.get(2).frame();
        assertTrue(body.frame().y() >= lastRow.y() + lastRow.h() - EPSILON,
                "the article body renders below the infographic");
    }

    private static com.tcs.contentGenerator.agent.design.infographic.InfographicSpec infographicSpec(
            String name, com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Archetype archetype,
            String kind) {
        return new com.tcs.contentGenerator.agent.design.infographic.InfographicSpec(
                name, archetype, 3, 6, 60, 220, archetype
                == com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Archetype.KPI_BARS,
                com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Background.ANY,
                new com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Shape(
                        kind, "primary", "secondary"));
    }

    @Test
    void kpiBarsInfographicUsesTheChevronRowShape() {
        DesignTemplate base = fixtureTemplate();
        DesignTemplate tallPage = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 800), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                base.decor());
        var spec = infographicSpec("kpi-bars",
                com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Archetype.KPI_BARS,
                "chevronBars");
        List<GeneratedArticle.Point> points = List.of(
                new GeneratedArticle.Point("NPS 72", "up from 68 last quarter"),
                new GeneratedArticle.Point("Growth 18%", "quarter on quarter"),
                new GeneratedArticle.Point("CSAT 4.6/5", "customer satisfaction"));
        GeneratedArticle article = new GeneratedArticle("Delivery headline",
                "A short delivery body with a couple of sentences.", null, points);
        SectionComposition section = new SectionComposition(NewsletterSection.DELIVERY_HIGHLIGHTS,
                SectionPattern.INFOGRAPHIC, List.of(article), "primary", null, null, null,
                List.of(), spec, points);
        DesignDocument document = new LayoutEngine().layout(
                new CompositionPlan("test", List.of(section)), tallPage, "Test Issue", "job-1");

        List<ImageBox> rows = document.pages().stream().flatMap(p -> p.components().stream())
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(b -> b.assetId() != null && b.assetId().startsWith("decor-infographic-"))
                .toList();
        assertTrue(rows.size() == 3, "one chevron row per point, got " + rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertTrue(rows.get(i).assetId().contains("chevronBars.primary.secondary." + (i + 1)),
                    "row " + i + " must encode the chevron shape kind: " + rows.get(i).assetId());
        }
    }

    @Test
    void cardGridInfographicPairsPointsIntoEqualHeightRows() {
        DesignTemplate base = fixtureTemplate();
        DesignTemplate tallPage = new DesignTemplate(base.name(),
                new Theme(new PageSize(PAGE_WIDTH, 800), base.theme().colors(),
                        base.theme().textStyles(), base.theme().spacing()),
                base.decor());
        var spec = infographicSpec("card-grid",
                com.tcs.contentGenerator.agent.design.infographic.InfographicSpec.Archetype.CARD_GRID,
                "pointCard");
        List<GeneratedArticle.Point> points = List.of(
                new GeneratedArticle.Point("Speed", "Ship faster than ever."),
                new GeneratedArticle.Point("Quality", "Fewer defects reach production."),
                new GeneratedArticle.Point("Scale", "Handles ten times the load."));
        GeneratedArticle article = new GeneratedArticle("Pillars headline",
                "A short pillars body.", null, points);
        SectionComposition section = new SectionComposition(NewsletterSection.INNOVATION_SPOTLIGHT,
                SectionPattern.INFOGRAPHIC, List.of(article), "primary", null, null, null,
                List.of(), spec, points);
        DesignDocument document = new LayoutEngine().layout(
                new CompositionPlan("test", List.of(section)), tallPage, "Test Issue", "job-1");

        List<Component> all = document.pages().stream().flatMap(p -> p.components().stream()).toList();
        List<ImageBox> cards = all.stream()
                .filter(ImageBox.class::isInstance).map(ImageBox.class::cast)
                .filter(b -> b.assetId() != null && b.assetId().startsWith("decor-infographic-"))
                .toList();
        assertTrue(cards.size() == 3, "one card per point, got " + cards.size());
        // first pair (2 cards) shares a row: same top + uniform height; the odd
        // third point gets a full-width card on the next row
        assertTrue(Math.abs(cards.get(0).frame().y() - cards.get(1).frame().y()) < EPSILON,
                "the first pair shares a row top");
        assertTrue(Math.abs(cards.get(0).frame().h() - cards.get(1).frame().h()) < EPSILON,
                "the first pair shares a uniform height");
        assertTrue(cards.get(1).frame().x() > cards.get(0).frame().x(), "pair is laid left to right");
        assertTrue(Math.abs(cards.get(2).frame().w() - tallPage.theme().pageSize().widthPt()
                        + 2 * MARGIN) < EPSILON,
                "the odd trailing card takes the full content width");
        assertTrue(cards.get(2).frame().y() > cards.get(0).frame().y() + EPSILON,
                "the trailing card starts a new row below the pair");
        assertTrue(all.stream().filter(c -> c.role() == ComponentRole.INFOGRAPHIC_LABEL).count() == 3,
                "each card carries its point's label as real text");
    }
}
