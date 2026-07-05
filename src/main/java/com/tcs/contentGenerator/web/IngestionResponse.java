package com.tcs.contentGenerator.web;

import java.util.List;

import com.tcs.contentGenerator.agent.compliance.ComplianceReport;
import com.tcs.contentGenerator.agent.compliance.ComplianceViolation;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.graphics.GraphicsReport;
import com.tcs.contentGenerator.agent.graphics.ImagePlacement;
import com.tcs.contentGenerator.agent.planning.NewsletterPlan;
import com.tcs.contentGenerator.agent.planning.PlannedItem;
import com.tcs.contentGenerator.agent.planning.SectionPlan;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.agent.validation.ValidationFlag;
import com.tcs.contentGenerator.agent.validation.ValidationReport;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.HeadingBlock;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.TableBlock;
import com.tcs.contentGenerator.document.TextBlock;
import com.tcs.contentGenerator.orchestrator.PipelineContext;

/** Summary of a pipeline run returned to the caller. */
public record IngestionResponse(
        String jobId,
        List<DocumentSummary> documents,
        List<ContentItemSummary> contentItems,
        PlanSummary plan,
        NewsletterSummary newsletter,
        ValidationSummary validation,
        ComplianceSummary compliance,
        DesignSummary design,
        GraphicsSummary graphics) {

    public record DocumentSummary(
            String filename,
            String type,
            int totalBlocks,
            int headings,
            int textBlocks,
            int tables,
            int images) {
    }

    /** Flattened view of a classified {@link ContentItem} for the API response. */
    public record ContentItemSummary(
            String title,
            String category,
            String type,
            String summary,
            List<String> keyMetrics,
            int sources) {
    }

    /** The planned issue: sections in reading order plus what was left out and why. */
    public record PlanSummary(
            String issueTitle,
            List<SectionSummary> sections,
            List<PlannedItemSummary> deferredItems) {
    }

    public record SectionSummary(String title, List<PlannedItemSummary> items) {
    }

    public record PlannedItemSummary(String title, String category, int score, String rationale) {
    }

    /** The written issue: every section's articles in reading order. */
    public record NewsletterSummary(String issueTitle, List<GeneratedSectionSummary> sections) {

        public int articleCount() {
            return sections.stream().mapToInt(s -> s.articles().size()).sum();
        }
    }

    public record GeneratedSectionSummary(String title, List<ArticleSummary> articles) {
    }

    public record ArticleSummary(String headline, String body, String sourceTitle) {
    }

    /** The fact-check verdict: every flag raised plus the export gate state. */
    public record ValidationSummary(
            int articlesChecked,
            int articlesSkipped,
            boolean exportBlocked,
            List<FlagSummary> flags) {
    }

    public record FlagSummary(
            String section,
            String article,
            String severity,
            String claim,
            String issue) {
    }

    /** The brand-rule check: every breach found, and whether it was auto-fixed. */
    public record ComplianceSummary(
            int articlesChecked,
            int articlesFixed,
            long unresolved,
            List<ViolationSummary> violations) {
    }

    public record ViolationSummary(
            String section,
            String article,
            String type,
            String found,
            String replacement,
            boolean fixed) {
    }

    /** The laid-out issue: how many pages/components the design composition agent produced. */
    public record DesignSummary(int pageCount, int componentCount) {
    }

    /** Which articles got a real image, and where each one came from. */
    public record GraphicsSummary(int articlesConsidered, int imagesPlaced, List<PlacementSummary> placements) {
    }

    public record PlacementSummary(String section, String article, String source) {
    }

    public static IngestionResponse from(PipelineContext context) {
        List<DocumentSummary> summaries = context.getDocuments().stream()
                .map(IngestionResponse::summarize)
                .toList();
        List<ContentItemSummary> items = context.getContentItems().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new IngestionResponse(context.getJobId(), summaries, items,
                summarize(context.getNewsletterPlan()),
                summarize(context.getGeneratedNewsletter()),
                summarize(context.getValidationReport()),
                summarize(context.getComplianceReport()),
                summarize(context.getDesignDocument()),
                summarize(context.getGraphicsReport()));
    }

    private static DesignSummary summarize(DesignDocument document) {
        if (document == null) {
            return null;
        }
        int components = document.pages().stream().mapToInt(page -> page.components().size()).sum();
        return new DesignSummary(document.pages().size(), components);
    }

    private static GraphicsSummary summarize(GraphicsReport report) {
        if (report == null) {
            return null;
        }
        List<PlacementSummary> placements = report.placements().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new GraphicsSummary(report.articlesConsidered(), report.imagesPlaced(), placements);
    }

    private static PlacementSummary summarize(ImagePlacement placement) {
        return new PlacementSummary(placement.sectionTitle(), placement.articleHeadline(),
                placement.source().name());
    }

    private static ComplianceSummary summarize(ComplianceReport report) {
        if (report == null) {
            return null;
        }
        List<ViolationSummary> violations = report.violations().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new ComplianceSummary(report.articlesChecked(), report.articlesFixed(),
                report.unresolvedCount(), violations);
    }

    private static ViolationSummary summarize(ComplianceViolation violation) {
        return new ViolationSummary(
                violation.sectionTitle(),
                violation.articleHeadline(),
                violation.type().label(),
                violation.found(),
                violation.replacement(),
                violation.fixed());
    }

    private static ValidationSummary summarize(ValidationReport report) {
        if (report == null) {
            return null;
        }
        List<FlagSummary> flags = report.flags().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new ValidationSummary(report.articlesChecked(), report.articlesSkipped(),
                report.exportBlocked(), flags);
    }

    private static FlagSummary summarize(ValidationFlag flag) {
        return new FlagSummary(
                flag.sectionTitle(),
                flag.articleHeadline(),
                flag.severity().label(),
                flag.claim(),
                flag.issue());
    }

    private static NewsletterSummary summarize(GeneratedNewsletter newsletter) {
        if (newsletter == null) {
            return null;
        }
        List<GeneratedSectionSummary> sections = newsletter.sections().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new NewsletterSummary(newsletter.issueTitle(), sections);
    }

    private static GeneratedSectionSummary summarize(GeneratedSection section) {
        List<ArticleSummary> articles = section.articles().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new GeneratedSectionSummary(section.section().title(), articles);
    }

    private static ArticleSummary summarize(GeneratedArticle article) {
        return new ArticleSummary(
                article.headline(),
                article.body(),
                article.source() == null ? null : article.source().item().title());
    }

    private static PlanSummary summarize(NewsletterPlan plan) {
        if (plan == null) {
            return null;
        }
        List<SectionSummary> sections = plan.sections().stream()
                .map(IngestionResponse::summarize)
                .toList();
        List<PlannedItemSummary> deferred = plan.deferredItems().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new PlanSummary(plan.issueTitle(), sections, deferred);
    }

    private static SectionSummary summarize(SectionPlan section) {
        List<PlannedItemSummary> items = section.items().stream()
                .map(IngestionResponse::summarize)
                .toList();
        return new SectionSummary(section.section().title(), items);
    }

    private static PlannedItemSummary summarize(PlannedItem planned) {
        return new PlannedItemSummary(
                planned.item().title(),
                planned.item().category().label(),
                planned.score(),
                planned.rationale());
    }

    private static ContentItemSummary summarize(ContentItem item) {
        return new ContentItemSummary(
                item.title(),
                item.category().label(),
                item.type().name(),
                item.summary(),
                item.keyMetrics(),
                item.sources().size());
    }

    private static DocumentSummary summarize(DocumentModel doc) {
        return new DocumentSummary(
                doc.metadata().originalFilename(),
                doc.metadata().type().name(),
                doc.blocks().size(),
                doc.blocksOf(HeadingBlock.class).size(),
                doc.blocksOf(TextBlock.class).size(),
                doc.blocksOf(TableBlock.class).size(),
                doc.blocksOf(ImageBlock.class).size());
    }
}
