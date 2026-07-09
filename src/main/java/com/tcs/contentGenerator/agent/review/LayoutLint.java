package com.tcs.contentGenerator.agent.review;

import java.util.ArrayList;
import java.util.List;

import com.tcs.contentGenerator.agent.design.layout.TextMeasurer;
import com.tcs.contentGenerator.design.Component;
import com.tcs.contentGenerator.design.ComponentRole;
import com.tcs.contentGenerator.design.DesignDocument;
import com.tcs.contentGenerator.design.Frame;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.ShapeBox;
import com.tcs.contentGenerator.design.TextBox;
import com.tcs.contentGenerator.design.TextStyle;
import com.tcs.contentGenerator.design.Theme;

/**
 * Deterministic, LLM-free pass over a positioned {@link DesignDocument}: text
 * overflow, frame overlaps, margin violations, low text-on-fill contrast, and
 * a section title orphaned at the bottom of a page. Reuses the same
 * {@link TextMeasurer} estimate the layout engine itself measures with, so a
 * finding here means the same yardstick that laid the page out now disagrees
 * with it (e.g. an editor shrank a frame in a later hand-edit).
 */
public class LayoutLint {

    private static final TextStyle DEFAULT_STYLE = new TextStyle("SansSerif", 10, "normal", "text", 14);
    /** How far down a page a section title has to sit to count as "at the bottom". */
    private static final double ORPHAN_ZONE_FRACTION = 0.75;

    private final TextMeasurer measurer = new TextMeasurer();
    private final double contrastRatioThreshold;
    private final double marginTolerancePt;
    private final double overflowTolerancePt;

    public LayoutLint(double contrastRatioThreshold, double marginTolerancePt, double overflowTolerancePt) {
        this.contrastRatioThreshold = contrastRatioThreshold;
        this.marginTolerancePt = marginTolerancePt;
        this.overflowTolerancePt = overflowTolerancePt;
    }

    public List<ReviewFinding> check(DesignDocument document) {
        Theme theme = document.theme();
        List<ReviewFinding> findings = new ArrayList<>();
        for (Page page : document.pages()) {
            List<Component> components = page.components();
            findings.addAll(checkOverflow(components, theme));
            findings.addAll(checkMargins(components, theme));
            findings.addAll(checkOverlaps(components));
            findings.addAll(checkContrast(components, theme));
            findings.addAll(checkOrphanedHeader(components, theme));
        }
        return findings;
    }

    private List<ReviewFinding> checkOverflow(List<Component> components, Theme theme) {
        List<ReviewFinding> out = new ArrayList<>();
        for (Component c : components) {
            if (!(c instanceof TextBox box)) {
                continue;
            }
            TextStyle style = theme.textStyles().getOrDefault(box.styleRef(), DEFAULT_STYLE);
            double naturalHeight = measurer.heightOf(box.text(), style, box.frame().w());
            if (naturalHeight > box.frame().h() + overflowTolerancePt) {
                out.add(new ReviewFinding(FindingSource.LAYOUT, "TEXT_OVERFLOW", FindingSeverity.HIGH,
                        box.id(), "Text needs about %.0fpt but the frame is only %.0fpt tall."
                                .formatted(naturalHeight, box.frame().h())));
            }
        }
        return out;
    }

    private List<ReviewFinding> checkMargins(List<Component> components, Theme theme) {
        List<ReviewFinding> out = new ArrayList<>();
        double margin = theme.spacing().marginPt();
        double pageWidth = theme.pageSize().widthPt();
        double pageHeight = theme.pageSize().heightPt();
        for (Component c : components) {
            if (c.role() == ComponentRole.DECORATION) {
                // decorations (masthead/footer bands) bleed to the page edges by design
                continue;
            }
            Frame f = c.frame();
            boolean violates = f.x() < margin - marginTolerancePt
                    || f.y() < margin - marginTolerancePt
                    || f.x() + f.w() > pageWidth - margin + marginTolerancePt
                    || f.y() + f.h() > pageHeight - margin + marginTolerancePt;
            if (violates) {
                out.add(new ReviewFinding(FindingSource.LAYOUT, "MARGIN_VIOLATION", FindingSeverity.MEDIUM,
                        c.id(), "Component extends outside the page margins."));
            }
        }
        return out;
    }

    /**
     * Pairwise overlap check, skipping any pair involving a {@link ShapeBox} —
     * dividers and section-icon dots are routinely placed behind or beside
     * text/images by design, so flagging those would just be noise. Components
     * with role {@code DECORATION} (masthead band, chips, stat card, footer)
     * are exempt for the same reason: they intentionally sit behind content.
     */
    private List<ReviewFinding> checkOverlaps(List<Component> components) {
        List<ReviewFinding> out = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            Component a = components.get(i);
            if (a instanceof ShapeBox || a.role() == ComponentRole.DECORATION) {
                continue;
            }
            for (int j = i + 1; j < components.size(); j++) {
                Component b = components.get(j);
                if (b instanceof ShapeBox || b.role() == ComponentRole.DECORATION) {
                    continue;
                }
                if (overlaps(a.frame(), b.frame())) {
                    out.add(new ReviewFinding(FindingSource.LAYOUT, "FRAME_OVERLAP", FindingSeverity.HIGH,
                            a.id(), "Overlaps component " + b.id() + "."));
                }
            }
        }
        return out;
    }

    private List<ReviewFinding> checkContrast(List<Component> components, Theme theme) {
        List<ReviewFinding> out = new ArrayList<>();
        for (Component c : components) {
            if (!(c instanceof TextBox box)) {
                continue;
            }
            if (sitsOnDecoration(box, components)) {
                // a DECORATION image (e.g. the masthead band) is behind this text —
                // its fill is pixels, not a theme color, so comparing against the
                // page background would be a false positive; composition already
                // chose the on-band style color deterministically
                continue;
            }
            TextStyle style = theme.textStyles().getOrDefault(box.styleRef(), DEFAULT_STYLE);
            String textHex = theme.colors().get(style.colorRole());
            String fillHex = backgroundFillFor(box, components, theme);
            if (textHex == null || fillHex == null) {
                continue;
            }
            double ratio = contrastRatio(textHex, fillHex);
            if (ratio < contrastRatioThreshold) {
                out.add(new ReviewFinding(FindingSource.LAYOUT, "LOW_CONTRAST", FindingSeverity.MEDIUM,
                        box.id(), "Text/background contrast ratio %.2f is below the %.1f minimum."
                                .formatted(ratio, contrastRatioThreshold)));
            }
        }
        return out;
    }

    /** A section title left as the last thing on a page has had its content pushed to the next page. */
    private List<ReviewFinding> checkOrphanedHeader(List<Component> components, Theme theme) {
        // trailing decorations (the footer band is appended last) don't count as content
        Component last = null;
        for (int i = components.size() - 1; i >= 0; i--) {
            if (components.get(i).role() != ComponentRole.DECORATION) {
                last = components.get(i);
                break;
            }
        }
        if (!(last instanceof TextBox box) || box.role() != ComponentRole.SECTION_TITLE) {
            return List.of();
        }
        double margin = theme.spacing().marginPt();
        double contentTop = margin;
        double contentBottom = theme.pageSize().heightPt() - margin;
        double lowerZoneStart = contentTop + (contentBottom - contentTop) * ORPHAN_ZONE_FRACTION;
        if (box.frame().y() >= lowerZoneStart) {
            return List.of(new ReviewFinding(FindingSource.LAYOUT, "ORPHANED_HEADER", FindingSeverity.LOW,
                    box.id(),
                    "Section title sits at the bottom of the page with no content beneath it "
                            + "— its section was pushed to the next page."));
        }
        return List.of();
    }

    private static boolean sitsOnDecoration(TextBox box, List<Component> components) {
        return components.stream().anyMatch(c -> c.role() == ComponentRole.DECORATION
                && overlaps(c.frame(), box.frame()));
    }

    private static String backgroundFillFor(TextBox box, List<Component> components, Theme theme) {
        String fill = theme.colors().get("background");
        for (Component c : components) {
            if (c instanceof ShapeBox shape && overlaps(shape.frame(), box.frame())) {
                String role = theme.colors().get(shape.fillColorRole());
                if (role != null) {
                    fill = role;
                }
            }
        }
        return fill;
    }

    private static boolean overlaps(Frame a, Frame b) {
        return a.x() < b.x() + b.w() && a.x() + a.w() > b.x()
                && a.y() < b.y() + b.h() && a.y() + a.h() > b.y();
    }

    /** WCAG 2.x contrast ratio between two hex colors, (lighter+0.05)/(darker+0.05). */
    private static double contrastRatio(String hexA, String hexB) {
        double l1 = relativeLuminance(hexA);
        double l2 = relativeLuminance(hexB);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(String hex) {
        int[] rgb = parseHex(hex);
        return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2]);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int[] parseHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        return new int[] {
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16)
        };
    }
}
