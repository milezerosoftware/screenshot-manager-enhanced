package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import java.util.Objects;

/**
 * Pure-Java navigation state calculations for the screenshot gallery grid,
 * pagination controls, viewport scroll checks, and single-image viewer.
 */
public final class GalleryNavigation {

    public enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    public record GridMoveResult(int selectedIndex, int page, boolean pageChanged) {
    }

    private GalleryNavigation() {
    }

    /**
     * Clamps a global selection index into {@code [0, totalItems - 1]}, or returns {@code -1}
     * when the gallery has no items or nothing is selected.
     */
    public static int clampSelection(int selectedIndex, int totalItems) {
        if (totalItems <= 0 || selectedIndex < 0) {
            return -1;
        }
        return Math.min(selectedIndex, totalItems - 1);
    }

    /**
     * Computes the 0-based page index containing {@code selectedIndex}.
     * Returns {@code 0} when {@code selectedIndex < 0}.
     */
    public static int pageForIndex(int selectedIndex, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        if (selectedIndex < 0) {
            return 0;
        }
        return selectedIndex / pageSize;
    }

    /**
     * Computes the next grid selection and page after pressing a directional arrow key.
     * <ul>
     *   <li>Empty list returns {@code (-1, 0, false)}.</li>
     *   <li>Unselected ({@code currentIndex < 0}) selects the first item on the clamped {@code currentPage}.</li>
     *   <li>{@code LEFT}/{@code RIGHT} step by {@code -1}/{@code +1} clamped to {@code [0, totalItems - 1]},
     *       automatically crossing page boundaries.</li>
     *   <li>{@code UP}/{@code DOWN} move between visual rows by {@code columns}, clamping cleanly on
     *       partial final rows and automatically crossing page boundaries when stepping past the
     *       first or last row of a page.</li>
     * </ul>
     */
    public static GridMoveResult moveGridSelection(
            int currentIndex,
            int currentPage,
            Direction direction,
            int totalItems,
            int columns,
            int pageSize) {
        Objects.requireNonNull(direction, "direction must not be null");
        if (columns <= 0) {
            throw new IllegalArgumentException("columns must be positive, got: " + columns);
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        if (totalItems <= 0) {
            return new GridMoveResult(-1, 0, false);
        }

        int safePage = GalleryDirectoryScanner.clampPage(currentPage, totalItems, pageSize);
        if (currentIndex < 0) {
            int firstOnPage = safePage * pageSize;
            return new GridMoveResult(firstOnPage, safePage, false);
        }

        int clampedCurrent = Math.min(currentIndex, totalItems - 1);
        int itemPage = pageForIndex(clampedCurrent, pageSize);
        int totalPages = GalleryDirectoryScanner.getTotalPages(totalItems, pageSize);

        int targetIndex;
        switch (direction) {
            case LEFT -> targetIndex = Math.max(0, clampedCurrent - 1);
            case RIGHT -> targetIndex = Math.min(totalItems - 1, clampedCurrent + 1);
            case UP -> {
                int pageStart = itemPage * pageSize;
                int localIdx = clampedCurrent - pageStart;
                int localRow = localIdx / columns;
                int localCol = localIdx % columns;
                if (localRow > 0) {
                    targetIndex = clampedCurrent - columns;
                } else if (itemPage > 0) {
                    int prevPageStart = (itemPage - 1) * pageSize;
                    int prevLastRowStart = ((pageSize - 1) / columns) * columns;
                    targetIndex = prevPageStart + Math.min(pageSize - 1, prevLastRowStart + localCol);
                } else {
                    targetIndex = clampedCurrent;
                }
            }
            case DOWN -> {
                int pageStart = itemPage * pageSize;
                int pageCount = Math.min(pageSize, totalItems - pageStart);
                int localIdx = clampedCurrent - pageStart;
                int localRow = localIdx / columns;
                int localCol = localIdx % columns;
                int lastRowOnPage = (pageCount - 1) / columns;
                if (localRow < lastRowOnPage) {
                    targetIndex = pageStart + Math.min(pageCount - 1, localIdx + columns);
                } else if (itemPage < totalPages - 1) {
                    int nextPageStart = (itemPage + 1) * pageSize;
                    int nextPageCount = Math.min(pageSize, totalItems - nextPageStart);
                    targetIndex = nextPageStart + Math.min(localCol, nextPageCount - 1);
                } else {
                    targetIndex = clampedCurrent;
                }
            }
            default -> throw new IllegalStateException("Unexpected direction: " + direction);
        }

        int targetPage = pageForIndex(targetIndex, pageSize);
        return new GridMoveResult(targetIndex, targetPage, targetPage != safePage);
    }

    /**
     * Computes the target page and selection after a Page Up ({@code pageDelta < 0})
     * or Page Down ({@code pageDelta > 0}) action.
     * <ul>
     *   <li>Empty list returns {@code (-1, 0, false)}.</li>
     *   <li>When {@code totalPages == 1}, Page Up jumps to index {@code 0} and Page Down
     *       jumps to index {@code totalItems - 1}.</li>
     *   <li>When {@code totalPages > 1}, flips by {@code pageDelta} (clamped to valid pages)
     *       and preserves the relative offset within the page (or {@code 0} if previously
     *       unselected), clamped to the target page's bounds.</li>
     * </ul>
     */
    public static GridMoveResult movePage(
            int currentPage,
            int currentIndex,
            int pageDelta,
            int totalItems,
            int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        if (totalItems <= 0) {
            return new GridMoveResult(-1, 0, false);
        }

        int totalPages = GalleryDirectoryScanner.getTotalPages(totalItems, pageSize);
        int safePage = GalleryDirectoryScanner.clampPage(currentPage, totalItems, pageSize);

        if (totalPages == 1) {
            if (pageDelta < 0) {
                return new GridMoveResult(0, 0, false);
            }
            if (pageDelta > 0) {
                return new GridMoveResult(totalItems - 1, 0, false);
            }
            int kept = currentIndex < 0 ? 0 : clampSelection(currentIndex, totalItems);
            return new GridMoveResult(kept, 0, false);
        }

        int targetPage = GalleryDirectoryScanner.clampPage(safePage + pageDelta, totalItems, pageSize);
        int offsetInPage = 0;
        if (currentIndex >= 0) {
            int clampedCurrent = Math.min(currentIndex, totalItems - 1);
            if (pageForIndex(clampedCurrent, pageSize) == safePage) {
                offsetInPage = clampedCurrent - (safePage * pageSize);
            }
        }

        int targetPageStart = targetPage * pageSize;
        int targetPageEnd = Math.min(totalItems, targetPageStart + pageSize) - 1;
        int targetIndex = Math.min(targetPageEnd, targetPageStart + offsetInPage);
        return new GridMoveResult(targetIndex, targetPage, targetPage != safePage);
    }

    /**
     * Computes the next index in single-image view when stepping by {@code delta}
     * (e.g. {@code -1} for Left, {@code +1} for Right), clamped to {@code [0, totalItems - 1]}.
     * Returns {@code -1} when {@code totalItems <= 0}.
     */
    public static int moveSingleImageIndex(int currentIndex, int delta, int totalItems) {
        if (totalItems <= 0) {
            return -1;
        }
        if (currentIndex < 0) {
            return 0;
        }
        int clamped = Math.min(currentIndex, totalItems - 1);
        return Math.max(0, Math.min(totalItems - 1, clamped + delta));
    }

    /**
     * Returns {@code true} when an item spanning {@code [itemTop, itemBottom]} is partially
     * or wholly outside the visible scroll viewport {@code [viewportTop, viewportBottom]}.
     * Returns {@code false} if either interval is not yet laid out (non-positive height).
     */
    public static boolean shouldScrollIntoView(int itemTop, int itemBottom, int viewportTop, int viewportBottom) {
        if (viewportBottom <= viewportTop || itemBottom <= itemTop) {
            return false;
        }
        return itemTop < viewportTop || itemBottom > viewportBottom;
    }

    /**
     * Computes the minimal clamped vertical scroll offset in {@code [0, maxScroll]}
     * needed to keep an item spanning {@code [itemTop, itemBottom]} (in content-relative
     * coordinates) visible within a viewport of height {@code viewportHeight} currently
     * positioned at {@code currentOffset}.
     * <p>
     * Returns {@code currentOffset} (clamped to {@code [0, maxScroll]}) when the item is
     * already fully visible or when geometry is degenerate ({@code viewportHeight <= 0}
     * or {@code itemBottom <= itemTop}).
     */
    public static double computeTargetScrollOffset(
            int itemTop,
            int itemBottom,
            double currentOffset,
            int viewportHeight,
            int maxScroll) {
        int safeMaxScroll = Math.max(0, maxScroll);
        double clampedCurrent = Math.max(0.0, Math.min(safeMaxScroll, currentOffset));
        if (safeMaxScroll == 0 || viewportHeight <= 0 || itemBottom <= itemTop) {
            return clampedCurrent;
        }

        double viewportTop = clampedCurrent;
        double viewportBottom = clampedCurrent + viewportHeight;

        if (itemTop < viewportTop) {
            return Math.max(0.0, Math.min(safeMaxScroll, itemTop));
        }
        if (itemBottom > viewportBottom) {
            double target = (itemBottom - itemTop > viewportHeight)
                    ? itemTop
                    : (itemBottom - viewportHeight);
            return Math.max(0.0, Math.min(safeMaxScroll, target));
        }
        return clampedCurrent;
    }
}
