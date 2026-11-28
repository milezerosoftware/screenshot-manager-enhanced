package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.GalleryNavigation.Direction;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.GalleryNavigation.GridMoveResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalleryNavigationTest {

    @Test
    void emptyGalleryReturnsUnselectedForAllNavigationOperations() {
        assertEquals(-1, GalleryNavigation.clampSelection(0, 0));
        assertEquals(-1, GalleryNavigation.clampSelection(-1, 0));
        assertEquals(new GridMoveResult(-1, 0, false),
                GalleryNavigation.moveGridSelection(-1, 0, Direction.RIGHT, 0, 3, 24));
        assertEquals(new GridMoveResult(-1, 0, false),
                GalleryNavigation.moveGridSelection(0, 1, Direction.DOWN, 0, 3, 24));
        assertEquals(new GridMoveResult(-1, 0, false),
                GalleryNavigation.movePage(0, -1, 1, 0, 24));
        assertEquals(-1, GalleryNavigation.moveSingleImageIndex(0, 1, 0));
        assertEquals(-1, GalleryNavigation.moveSingleImageIndex(0, -1, 0));
    }

    @Test
    void unselectedArrowPressSelectsFirstItemOnCurrentPage() {
        // Page 0
        assertEquals(new GridMoveResult(0, 0, false),
                GalleryNavigation.moveGridSelection(-1, 0, Direction.RIGHT, 50, 3, 24));
        assertEquals(new GridMoveResult(0, 0, false),
                GalleryNavigation.moveGridSelection(-1, 0, Direction.UP, 50, 3, 24));

        // Page 1 starts at index 24
        assertEquals(new GridMoveResult(24, 1, false),
                GalleryNavigation.moveGridSelection(-1, 1, Direction.DOWN, 50, 3, 24));
        assertEquals(new GridMoveResult(24, 1, false),
                GalleryNavigation.moveGridSelection(-1, 1, Direction.LEFT, 50, 3, 24));

        // Page 2 (clamped if too high) starts at index 48
        assertEquals(new GridMoveResult(48, 2, false),
                GalleryNavigation.moveGridSelection(-1, 99, Direction.RIGHT, 50, 3, 24));
    }

    @Test
    void singleItemDirectoryStaysAtZeroForAllDirections() {
        for (Direction dir : Direction.values()) {
            assertEquals(new GridMoveResult(0, 0, false),
                    GalleryNavigation.moveGridSelection(0, 0, dir, 1, 3, 24));
        }
        assertEquals(new GridMoveResult(0, 0, false),
                GalleryNavigation.movePage(0, 0, -1, 1, 24));
        assertEquals(new GridMoveResult(0, 0, false),
                GalleryNavigation.movePage(0, 0, 1, 1, 24));
    }

    @Test
    void leftAndRightStepAcrossRowsAndPagesAndClampAtBounds() {
        // Clamped at start
        assertEquals(new GridMoveResult(0, 0, false),
                GalleryNavigation.moveGridSelection(0, 0, Direction.LEFT, 30, 3, 24));

        // Within row and wrapping across row end
        assertEquals(new GridMoveResult(3, 0, false),
                GalleryNavigation.moveGridSelection(2, 0, Direction.RIGHT, 30, 3, 24));
        assertEquals(new GridMoveResult(2, 0, false),
                GalleryNavigation.moveGridSelection(3, 0, Direction.LEFT, 30, 3, 24));

        // Crossing forward from page 0 (last index 23) to page 1 (index 24)
        assertEquals(new GridMoveResult(24, 1, true),
                GalleryNavigation.moveGridSelection(23, 0, Direction.RIGHT, 30, 3, 24));

        // Crossing backward from page 1 (index 24) to page 0 (index 23)
        assertEquals(new GridMoveResult(23, 0, true),
                GalleryNavigation.moveGridSelection(24, 1, Direction.LEFT, 30, 3, 24));

        // Clamped at end
        assertEquals(new GridMoveResult(29, 1, false),
                GalleryNavigation.moveGridSelection(29, 1, Direction.RIGHT, 30, 3, 24));
    }

    @Test
    void upAndDownMoveByColumnsAndHandlePartialFinalRows() {
        // 5 items at 3 columns: row 0 = [0, 1, 2], row 1 = [3, 4]
        assertEquals(new GridMoveResult(3, 0, false),
                GalleryNavigation.moveGridSelection(0, 0, Direction.DOWN, 5, 3, 24));
        // Column 2 on row 0 moves down to partial last row -> clamps to last item (4)
        assertEquals(new GridMoveResult(4, 0, false),
                GalleryNavigation.moveGridSelection(2, 0, Direction.DOWN, 5, 3, 24));
        // Already on last row -> stays on same item
        assertEquals(new GridMoveResult(3, 0, false),
                GalleryNavigation.moveGridSelection(3, 0, Direction.DOWN, 5, 3, 24));
        assertEquals(new GridMoveResult(4, 0, false),
                GalleryNavigation.moveGridSelection(4, 0, Direction.DOWN, 5, 3, 24));
        // Already on top row -> stays on same item
        assertEquals(new GridMoveResult(2, 0, false),
                GalleryNavigation.moveGridSelection(2, 0, Direction.UP, 5, 3, 24));
        // Up from row 1 -> returns to same column on row 0
        assertEquals(new GridMoveResult(1, 0, false),
                GalleryNavigation.moveGridSelection(4, 0, Direction.UP, 5, 3, 24));
    }

    @Test
    void upAndDownCrossPageBoundariesPreservingColumnOrClampingPartialPage() {
        // 25 items at 3 columns, pageSize 24:
        // page 0 last row = [21, 22, 23], page 1 = [24]
        assertEquals(new GridMoveResult(24, 1, true),
                GalleryNavigation.moveGridSelection(21, 0, Direction.DOWN, 25, 3, 24));
        assertEquals(new GridMoveResult(24, 1, true),
                GalleryNavigation.moveGridSelection(23, 0, Direction.DOWN, 25, 3, 24));

        // Up from page 1 col 0 (index 24) lands on page 0 last row col 0 (index 21)
        assertEquals(new GridMoveResult(21, 0, true),
                GalleryNavigation.moveGridSelection(24, 1, Direction.UP, 25, 3, 24));

        // 60 items at 6 columns, pageSize 36:
        // page 0 last row = [30..35], page 1 row 0 = [36..41]
        assertEquals(new GridMoveResult(39, 1, true),
                GalleryNavigation.moveGridSelection(33, 0, Direction.DOWN, 60, 6, 36));
        assertEquals(new GridMoveResult(33, 0, true),
                GalleryNavigation.moveGridSelection(39, 1, Direction.UP, 60, 6, 36));
    }

    @Test
    void movePageFlipsPagesPreservingRelativeOffsetAndClampsShortLastPage() {
        // 50 items, pageSize 24 -> 3 pages: [0..23], [24..47], [48..49]
        // Unselected on page 0 -> Page Down lands on first item of page 1 (24)
        assertEquals(new GridMoveResult(24, 1, true),
                GalleryNavigation.movePage(0, -1, 1, 50, 24));

        // Selected offset 5 on page 0 -> Page Down lands on offset 5 of page 1 (29)
        assertEquals(new GridMoveResult(29, 1, true),
                GalleryNavigation.movePage(0, 5, 1, 50, 24));

        // Selected offset 5 on page 1 (29) -> Page Down to short page 2 clamps to last item (49)
        assertEquals(new GridMoveResult(49, 2, true),
                GalleryNavigation.movePage(1, 29, 1, 50, 24));

        // Selected offset 1 on page 2 (49) -> Page Up lands on offset 1 of page 1 (25)
        assertEquals(new GridMoveResult(25, 1, true),
                GalleryNavigation.movePage(2, 49, -1, 50, 24));

        // Page Up at page 0 stays on page 0
        assertEquals(new GridMoveResult(5, 0, false),
                GalleryNavigation.movePage(0, 5, -1, 50, 24));

        // Page Down at last page stays on last page
        assertEquals(new GridMoveResult(49, 2, false),
                GalleryNavigation.movePage(2, 49, 1, 50, 24));
    }

    @Test
    void movePageOnSinglePageJumpsToStartOrEnd() {
        // Single page of 10 items (pageSize 24)
        assertEquals(new GridMoveResult(9, 0, false),
                GalleryNavigation.movePage(0, 2, 1, 10, 24));
        assertEquals(new GridMoveResult(0, 0, false),
                GalleryNavigation.movePage(0, 7, -1, 10, 24));
        assertEquals(new GridMoveResult(9, 0, false),
                GalleryNavigation.movePage(0, -1, 1, 10, 24));
        assertEquals(new GridMoveResult(0, 0, false),
                GalleryNavigation.movePage(0, -1, -1, 10, 24));
    }

    @Test
    void clampSelectionAndPageForIndexHandleColumnChangesAndBounds() {
        assertEquals(-1, GalleryNavigation.clampSelection(-5, 10));
        assertEquals(0, GalleryNavigation.clampSelection(0, 10));
        assertEquals(9, GalleryNavigation.clampSelection(25, 10));

        // Index 40 across different column/page sizes (3->24, 6->36, 8->48)
        assertEquals(1, GalleryNavigation.pageForIndex(40, GalleryDirectoryScanner.pageSizeForColumns(3)));
        assertEquals(1, GalleryNavigation.pageForIndex(40, GalleryDirectoryScanner.pageSizeForColumns(6)));
        assertEquals(0, GalleryNavigation.pageForIndex(40, GalleryDirectoryScanner.pageSizeForColumns(8)));
        assertEquals(0, GalleryNavigation.pageForIndex(-1, 24));
    }

    @Test
    void moveSingleImageIndexStepsAndClampsAtBoundaries() {
        assertEquals(0, GalleryNavigation.moveSingleImageIndex(0, -1, 5));
        assertEquals(1, GalleryNavigation.moveSingleImageIndex(0, 1, 5));
        assertEquals(3, GalleryNavigation.moveSingleImageIndex(4, -1, 5));
        assertEquals(4, GalleryNavigation.moveSingleImageIndex(4, 1, 5));
        assertEquals(0, GalleryNavigation.moveSingleImageIndex(0, 1, 1));
        assertEquals(0, GalleryNavigation.moveSingleImageIndex(0, -1, 1));
        assertEquals(0, GalleryNavigation.moveSingleImageIndex(-1, 1, 5));
    }

    @Test
    void shouldScrollIntoViewDetectsClippedAndUnlaidOutGeometry() {
        // Fully visible inside viewport [100, 400]
        assertFalse(GalleryNavigation.shouldScrollIntoView(100, 200, 100, 400));
        assertFalse(GalleryNavigation.shouldScrollIntoView(150, 350, 100, 400));
        assertFalse(GalleryNavigation.shouldScrollIntoView(300, 400, 100, 400));

        // Clipped or completely above viewport
        assertTrue(GalleryNavigation.shouldScrollIntoView(99, 200, 100, 400));
        assertTrue(GalleryNavigation.shouldScrollIntoView(0, 80, 100, 400));

        // Clipped or completely below viewport
        assertTrue(GalleryNavigation.shouldScrollIntoView(350, 401, 100, 400));
        assertTrue(GalleryNavigation.shouldScrollIntoView(450, 550, 100, 400));

        // Item taller than viewport
        assertTrue(GalleryNavigation.shouldScrollIntoView(50, 450, 100, 400));

        // Unlaid-out / degenerate bounds return false
        assertFalse(GalleryNavigation.shouldScrollIntoView(0, 0, 100, 400));
        assertFalse(GalleryNavigation.shouldScrollIntoView(150, 250, 100, 100));
    }

    @Test
    void computeTargetScrollOffsetScrollsMinimallyAndClampsWithoutDrift() {
        // Already fully visible inside [100, 400] -> keeps currentOffset
        assertEquals(100.0, GalleryNavigation.computeTargetScrollOffset(100, 200, 100.0, 300, 500));
        assertEquals(100.0, GalleryNavigation.computeTargetScrollOffset(200, 400, 100.0, 300, 500));

        // Below viewport -> scrolls down minimally so itemBottom aligns with viewportBottom
        assertEquals(50.0, GalleryNavigation.computeTargetScrollOffset(250, 350, 0.0, 300, 500));
        // Consecutive rapid DOWN presses advance deterministically from prior target
        assertEquals(150.0, GalleryNavigation.computeTargetScrollOffset(350, 450, 50.0, 300, 500));
        // Immediate UP back to an item above the target viewport scrolls back cleanly
        assertEquals(80.0, GalleryNavigation.computeTargetScrollOffset(80, 180, 150.0, 300, 500));

        // Clamps to [0, maxScroll]
        assertEquals(0.0, GalleryNavigation.computeTargetScrollOffset(-10, 80, 50.0, 300, 500));
        assertEquals(500.0, GalleryNavigation.computeTargetScrollOffset(750, 850, 100.0, 300, 500));

        // Item taller than viewport -> aligns itemTop to viewportTop
        assertEquals(120.0, GalleryNavigation.computeTargetScrollOffset(120, 520, 0.0, 300, 500));
        assertEquals(120.0, GalleryNavigation.computeTargetScrollOffset(120, 520, 200.0, 300, 500));

        // Zero/negative maxScroll or degenerate geometry -> returns clamped current offset
        assertEquals(0.0, GalleryNavigation.computeTargetScrollOffset(250, 350, 0.0, 300, 0));
        assertEquals(0.0, GalleryNavigation.computeTargetScrollOffset(250, 350, 50.0, 300, -10));
        assertEquals(100.0, GalleryNavigation.computeTargetScrollOffset(0, 0, 100.0, 300, 500));
        assertEquals(100.0, GalleryNavigation.computeTargetScrollOffset(100, 200, 100.0, 0, 500));
    }

    @Test
    void invalidArgumentsThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> GalleryNavigation.pageForIndex(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> GalleryNavigation.moveGridSelection(0, 0, Direction.RIGHT, 10, 0, 24));
        assertThrows(IllegalArgumentException.class,
                () -> GalleryNavigation.moveGridSelection(0, 0, Direction.RIGHT, 10, 3, 0));
        assertThrows(IllegalArgumentException.class,
                () -> GalleryNavigation.movePage(0, 0, 1, 10, 0));
        assertThrows(NullPointerException.class,
                () -> GalleryNavigation.moveGridSelection(0, 0, null, 10, 3, 24));
    }
}
