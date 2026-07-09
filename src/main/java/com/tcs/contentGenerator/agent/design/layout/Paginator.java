package com.tcs.contentGenerator.agent.design.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.tcs.contentGenerator.design.Component;
import com.tcs.contentGenerator.design.Page;
import com.tcs.contentGenerator.design.Theme;

/**
 * Owns the running vertical cursor during layout: which page a component
 * lands on, when a new page starts, and component id assignment. Components
 * are always placed flush against the left margin at {@link #x()}; callers
 * needing a different x (multi-column patterns) build the {@code Frame}
 * themselves using {@link #x()} and {@link #contentWidth()} as the reference.
 */
public class Paginator {

    private final double margin;
    private final double contentWidth;
    private final double contentHeight;
    private final AtomicInteger idSeq = new AtomicInteger();
    private final List<List<Component>> pages = new ArrayList<>();
    private double y;

    public Paginator(Theme theme) {
        this.margin = theme.spacing().marginPt();
        this.contentWidth = theme.pageSize().widthPt() - 2 * margin;
        this.contentHeight = theme.pageSize().heightPt() - 2 * margin;
        startPage();
    }

    private void startPage() {
        pages.add(new ArrayList<>());
        y = margin;
    }

    public double x() {
        return margin;
    }

    public double y() {
        return y;
    }

    public double contentWidth() {
        return contentWidth;
    }

    private double remaining() {
        return margin + contentHeight - y;
    }

    /**
     * Reserves {@code desiredHeightPt} of vertical space, starting a new page
     * first if it would not fit on the current one. Returns the height to
     * actually use, clamped ({@link OverflowResolver}) if it exceeds what a
     * whole page could ever hold. Does not move the cursor — call
     * {@link #advance(double)} after placing the component.
     */
    public double reserve(double desiredHeightPt, String label) {
        double clamped = OverflowResolver.clamp(desiredHeightPt, contentHeight, label);
        if (clamped > remaining() && y > margin) {
            startPage();
        }
        return clamped;
    }

    public void advance(double amountPt) {
        y += amountPt;
    }

    public void add(Component component) {
        pages.get(pages.size() - 1).add(component);
    }

    /** Index of the page the cursor is currently on (0-based). */
    public int pageIndex() {
        return pages.size() - 1;
    }

    /** Number of components already placed on the current page. */
    public int positionOnCurrentPage() {
        return pages.get(pages.size() - 1).size();
    }

    /**
     * Inserts a component at a recorded position on a specific page — used for
     * backdrops whose extent is only known after their content is laid out
     * (e.g. a section tint band): inserting at the position captured before the
     * section keeps the backdrop painting beneath it (paint order = list order).
     */
    public void insertOnPage(int pageIndex, int position, Component component) {
        pages.get(pageIndex).add(position, component);
    }

    public String nextId() {
        return "cmp-" + idSeq.incrementAndGet();
    }

    public List<Page> finish() {
        List<Page> result = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            result.add(new Page("page-" + (i + 1), List.copyOf(pages.get(i))));
        }
        return result;
    }
}
