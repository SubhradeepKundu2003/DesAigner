package com.tcs.contentGenerator.agent.design;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tcs.contentGenerator.agent.design.layout.LayoutEngine;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.orchestrator.Agent;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

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

    private final TemplateCatalog templates;
    private final LayoutEngine layoutEngine;

    public DesignCompositionAgent(TemplateCatalog templates, LayoutEngine layoutEngine) {
        this.templates = templates;
        this.layoutEngine = layoutEngine;
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
        List<SectionComposition> sections = newsletter.sections().stream()
                .filter(section -> !section.articles().isEmpty())
                .map(this::compose)
                .toList();
        CompositionPlan plan = new CompositionPlan(template.name(), sections);
        DesignDocument document = layoutEngine.layout(plan, template, newsletter.issueTitle(), context.getJobId());
        context.setDesignDocument(document);
        log.info("Design composition produced {} page(s) from {} section(s) using template '{}'",
                document.pages().size(), sections.size(), template.name());
    }

    private SectionComposition compose(GeneratedSection section) {
        NewsletterSection newsletterSection = section.section();
        List<GeneratedArticle> articles = section.articles();
        String iconColorRole = IconMatcher.colorRoleFor(newsletterSection);

        if (newsletterSection == NewsletterSection.LEADERSHIP_MESSAGE) {
            return new SectionComposition(newsletterSection, SectionPattern.HERO, articles, iconColorRole, null, null);
        }
        if (newsletterSection == NewsletterSection.UPCOMING_EVENTS) {
            return new SectionComposition(newsletterSection, SectionPattern.EVENT_LIST, articles, iconColorRole,
                    null, null);
        }
        if (articles.size() == 1) {
            String[] stat = extractStat(articles.get(0));
            if (stat != null) {
                return new SectionComposition(newsletterSection, SectionPattern.STAT_CALLOUT, articles,
                        iconColorRole, stat[0], stat[1]);
            }
        }
        if (articles.size() == 2) {
            return new SectionComposition(newsletterSection, SectionPattern.TWO_COLUMN, articles, iconColorRole,
                    null, null);
        }
        return new SectionComposition(newsletterSection, SectionPattern.STANDARD, articles, iconColorRole, null, null);
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
