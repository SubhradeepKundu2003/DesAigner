package com.tcs.contentGenerator.agent.graphics;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tcs.contentGenerator.agent.design.Decor;
import com.tcs.contentGenerator.agent.design.decor.PhotoEffects;
import com.tcs.contentGenerator.agent.generation.GeneratedArticle;
import com.tcs.contentGenerator.agent.generation.GeneratedNewsletter;
import com.tcs.contentGenerator.agent.generation.GeneratedSection;
import com.tcs.contentGenerator.agent.planning.NewsletterSection;
import com.tcs.contentGenerator.agent.understanding.ContentItem;
import com.tcs.contentGenerator.design.Asset;
import com.tcs.contentGenerator.design.Component;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.ImageBox;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.SourceLink;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.Theme;
import com.tcs.contentGenerator.document.DocumentModel;
import com.tcs.contentGenerator.document.ImageBlock;
import com.tcs.contentGenerator.document.SourceRef;
import com.tcs.contentGenerator.storage.StorageService;

/**
 * Enriches one {@link Page} at a time with images: for every {@code ARTICLE_BODY}
 * that has a source-document image or a matching brand asset and enough free
 * space beneath it, adds an {@link ImageBox}. One instance per pipeline run —
 * it tracks which source-document images have already been used so the same
 * photo isn't placed twice, and hands out globally-unique asset/component ids.
 *
 * <p>No {@code LayoutEngine} change was needed: instead of reflowing existing
 * text, this looks at the free vertical space already left between an
 * article's body and whatever comes next on the page and fits an image inside
 * it — skipping the article silently if that gap is too small. A known v1
 * trade-off, same spirit as {@code OverflowResolver}'s clamping.
 */
final class ImagePlacer {

    private static final Logger log = LoggerFactory.getLogger(ImagePlacer.class);
    private static final double GAP_PT = 8;
    private static final double MIN_IMAGE_HEIGHT_PT = 48;
    private static final double DEFAULT_ASPECT = 1.5;

    /** Raster pixels per design-model point when baking a treated photo. */
    private static final int TREATMENT_SCALE = 2;

    private final GeneratedNewsletter newsletter;
    private final List<DocumentModel> documents;
    private final StorageService storage;
    private final AssetLibrary assetLibrary;
    private final Decor.Photo photoDecor;
    private final String jobId;
    private final Set<String> usedSourceRefs = new HashSet<>();
    /** Sections already illustrated via a reserved slot — the gap pass skips them. */
    private final Set<String> sectionsWithImage = new HashSet<>();
    private final List<Asset> assets = new ArrayList<>();
    private final List<ImagePlacement> placements = new ArrayList<>();
    private final AtomicInteger sequence;
    private int articlesConsidered;

    ImagePlacer(GeneratedNewsletter newsletter, List<DocumentModel> documents, List<Asset> existingAssets,
            StorageService storage, AssetLibrary assetLibrary, Decor.Photo photoDecor, String jobId) {
        this.newsletter = newsletter;
        this.documents = documents;
        this.storage = storage;
        this.assetLibrary = assetLibrary;
        this.photoDecor = photoDecor;
        this.jobId = jobId;
        this.assets.addAll(existingAssets);
        this.sequence = new AtomicInteger(existingAssets.size());
    }

    /**
     * Pass 1 — fill (or drop) the empty photo slots the layout reserved. Run
     * over <em>all</em> pages before any {@link #scavengeGaps} call: the
     * one-image-per-section guard only works if every slot fill lands in
     * {@code sectionsWithImage} before the gap pass starts (a section's slot
     * can sit on a later page than its first article).
     */
    Page fillSlots(Page page) {
        List<Component> components = fillReservedSlots(page.components());
        return components == page.components() ? page : new Page(page.id(), components);
    }

    /** Pass 2 — legacy gap-scavenging for articles whose section has no image yet. */
    Page scavengeGaps(Page page, Theme theme) {
        List<Component> components = page.components();
        List<Component> additions = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            if (!(components.get(i) instanceof TextBox body) || body.role() != ComponentRole.ARTICLE_BODY
                    || body.source() == null) {
                continue;
            }
            articlesConsidered++;
            SourceLink link = body.source();
            if (sectionsWithImage.contains(link.sectionTitle())) {
                continue;
            }
            ArticleMatch match = matchArticle(link);
            ImageCandidate candidate = resolveCandidate(match);
            if (candidate == null) {
                continue;
            }
            int[] dims = readDimensions(candidate.storedRef());
            Optional<Frame> frame = availableFrame(components, i, body.frame(), theme, dims);
            if (frame.isEmpty()) {
                continue;
            }
            place(link, candidate, dims, frame.get(), additions);
        }
        if (additions.isEmpty()) {
            return page;
        }
        List<Component> merged = new ArrayList<>(components);
        merged.addAll(additions);
        return new Page(page.id(), merged);
    }

    /**
     * The layout engine reserves empty {@code IMAGE_PLACEHOLDER} boxes
     * ({@code assetId} null) below sections when the template asks for photos.
     * Each gets the best available image (same source-doc-first resolution as
     * the gap path, with the section's brand default / GENERIC as fallback) —
     * or is removed, so no dashed placeholder ever reaches the export.
     */
    private List<Component> fillReservedSlots(List<Component> components) {
        boolean changed = false;
        List<Component> out = new ArrayList<>(components.size());
        for (Component c : components) {
            if (!(c instanceof ImageBox slot) || slot.assetId() != null
                    || slot.role() != ComponentRole.IMAGE_PLACEHOLDER || slot.source() == null) {
                out.add(c);
                continue;
            }
            changed = true;
            articlesConsidered++;
            ImageCandidate candidate = resolveCandidate(matchArticle(slot.source()));
            if (candidate == null) {
                log.debug("No image available for reserved slot {} — dropping it", slot.id());
                continue;
            }
            int n = sequence.incrementAndGet();
            String assetId = "graphics-asset-" + n;
            int[] dims = readDimensions(candidate.storedRef());
            StoredImage image = treatedOrRaw(candidate.storedRef(), slot.frame(), n, dims);
            assets.add(new Asset(assetId, "image", image.storedRef(), image.width(), image.height()));
            out.add(new ImageBox(slot.id(), slot.role(), slot.frame(), slot.z(), slot.locked(),
                    slot.source(), assetId, slot.altText()));
            if (candidate.source() == ImageSource.SOURCE_DOCUMENT) {
                usedSourceRefs.add(candidate.storedRef());
            }
            sectionsWithImage.add(slot.source().sectionTitle());
            placements.add(new ImagePlacement(slot.source().sectionTitle(),
                    slot.source().articleHeadline(), candidate.source(), candidate.storedRef()));
        }
        return changed ? out : components;
    }

    List<Asset> assets() {
        return assets;
    }

    List<ImagePlacement> placements() {
        return placements;
    }

    int articlesConsidered() {
        return articlesConsidered;
    }

    private void place(SourceLink link, ImageCandidate candidate, int[] dims, Frame frame, List<Component> additions) {
        int n = sequence.incrementAndGet();
        String assetId = "graphics-asset-" + n;
        StoredImage image = treatedOrRaw(candidate.storedRef(), frame, n, dims);
        assets.add(new Asset(assetId, "image", image.storedRef(), image.width(), image.height()));
        additions.add(new ImageBox("graphics-img-" + n, ComponentRole.IMAGE_PLACEHOLDER, frame, 0, false,
                link, assetId, link.articleHeadline()));
        if (candidate.source() == ImageSource.SOURCE_DOCUMENT) {
            usedSourceRefs.add(candidate.storedRef());
        }
        placements.add(new ImagePlacement(link.sectionTitle(), link.articleHeadline(),
                candidate.source(), candidate.storedRef()));
    }

    private Optional<Frame> availableFrame(List<Component> pageComponents, int selfIndex, Frame anchor,
            Theme theme, int[] dims) {
        double bottom = anchor.y() + anchor.h();
        double boundary = theme.pageSize().heightPt() - theme.spacing().marginPt();
        for (int j = 0; j < pageComponents.size(); j++) {
            if (j == selfIndex) {
                continue;
            }
            double y = pageComponents.get(j).frame().y();
            if (y >= bottom - 0.01) {
                boundary = Math.min(boundary, y);
            }
        }
        double available = boundary - bottom - GAP_PT * 2;
        if (available < MIN_IMAGE_HEIGHT_PT) {
            return Optional.empty();
        }
        double aspect = dims != null && dims[1] > 0 ? (double) dims[0] / dims[1] : DEFAULT_ASPECT;
        double height = Math.min(available, anchor.w() / aspect);
        double width = height * aspect;
        return Optional.of(new Frame(anchor.x(), bottom + GAP_PT, width, height));
    }

    /**
     * Applies the template's photo treatment (crop-to-fill + clip + shadow,
     * baked at {@value TREATMENT_SCALE}px/pt by {@link PhotoEffects}) and
     * stores the result under the job's {@code decor/} folder. No treatment
     * configured — or a failed one — keeps the raw image (failure isolation:
     * an undecodable photo degrades to its untreated self, never the run).
     */
    private StoredImage treatedOrRaw(String storedRef, Frame frame, int n, int[] dims) {
        if (photoDecor == null) {
            return new StoredImage(storedRef, dims == null ? null : dims[0], dims == null ? null : dims[1]);
        }
        try {
            int outW = (int) Math.round(frame.w() * TREATMENT_SCALE);
            int outH = (int) Math.round(frame.h() * TREATMENT_SCALE);
            byte[] treated = PhotoEffects.treat(storage.retrieve(storedRef), outW, outH,
                    photoDecor, photoDecor.cornerRadiusPt() * TREATMENT_SCALE);
            String ref = storage.store("jobs/" + jobId + "/decor/photo-" + n + ".png", treated);
            return new StoredImage(ref, outW, outH);
        } catch (IOException | RuntimeException e) {
            log.warn("Photo treatment failed for {} — placing the untreated image", storedRef, e);
            return new StoredImage(storedRef, dims == null ? null : dims[0], dims == null ? null : dims[1]);
        }
    }

    private record StoredImage(String storedRef, Integer width, Integer height) {
    }

    private int[] readDimensions(String storedRef) {
        try {
            byte[] bytes = storage.retrieve(storedRef);
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            return image == null ? null : new int[] {image.getWidth(), image.getHeight()};
        } catch (IOException | RuntimeException e) {
            log.debug("Could not read image dimensions for {}", storedRef, e);
            return null;
        }
    }

    private ImageCandidate resolveCandidate(ArticleMatch match) {
        for (ImageBlock block : imagesForArticle(match)) {
            if (!usedSourceRefs.contains(block.storedRef())) {
                return new ImageCandidate(block.storedRef(), ImageSource.SOURCE_DOCUMENT);
            }
        }
        if (match != null) {
            return assetLibrary.findFor(match.section())
                    .map(ref -> new ImageCandidate(ref, ImageSource.BRAND_ASSET))
                    .orElse(null);
        }
        return null;
    }

    private List<ImageBlock> imagesForArticle(ArticleMatch match) {
        if (match == null || match.article().source() == null) {
            return List.of();
        }
        ContentItem item = match.article().source().item();
        Set<String> docNames = item.sources().stream().map(SourceRef::documentName).collect(Collectors.toSet());
        if (docNames.isEmpty()) {
            return List.of();
        }
        return documents.stream()
                .filter(d -> docNames.contains(d.metadata().originalFilename()))
                .flatMap(d -> d.blocksOf(ImageBlock.class).stream())
                .toList();
    }

    private ArticleMatch matchArticle(SourceLink link) {
        if (newsletter == null) {
            return null;
        }
        for (GeneratedSection section : newsletter.sections()) {
            if (!section.section().title().equals(link.sectionTitle())) {
                continue;
            }
            for (GeneratedArticle article : section.articles()) {
                if (article.headline().equals(link.articleHeadline())) {
                    return new ArticleMatch(section.section(), article);
                }
            }
        }
        return null;
    }

    private record ArticleMatch(NewsletterSection section, GeneratedArticle article) {
    }

    private record ImageCandidate(String storedRef, ImageSource source) {
    }
}
