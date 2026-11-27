package com.milezerosoftware.mc.screenshotmanagerenhanced.client.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ThumbnailSizing}.
 */
public class ThumbnailSizingTest {

    @Test
    void testStandard16By9Downscale() {
        ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(1920, 1080, 320);
        assertEquals(320, dims.width());
        assertEquals(180, dims.height());
    }

    @Test
    void testUltrawideDownscalePreservesAspectRatio() {
        ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(2560, 1080, 320);
        assertEquals(320, dims.width());
        assertEquals(135, dims.height());
    }

    @Test
    void testPortraitDownscalePreservesAspectRatio() {
        ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(1080, 1920, 320);
        assertEquals(320, dims.width());
        assertEquals(569, dims.height());
    }

    @Test
    void testSquareDownscale() {
        ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(1024, 1024, 256);
        assertEquals(256, dims.width());
        assertEquals(256, dims.height());
    }

    @Test
    void testNeverUpscalesWhenSourceSmallerThanTarget() {
        ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(200, 150, 1920);
        assertEquals(200, dims.width());
        assertEquals(150, dims.height());
    }

    @Test
    void testExactWidthMatchReturnsOriginalDimensions() {
        ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(320, 240, 320);
        assertEquals(320, dims.width());
        assertEquals(240, dims.height());
    }

    @Test
    void testExtremeWideImageClampsHeightToMinimumOne() {
        ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(10000, 5, 100);
        assertEquals(100, dims.width());
        assertEquals(1, dims.height());
    }

    @Test
    void testOneByOneSourceNeverUpscales() {
        ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(1, 1, 500);
        assertEquals(1, dims.width());
        assertEquals(1, dims.height());
    }

    @Test
    void testTargetWidthOneProducesMinimumOneByOneOrScaledHeight() {
        ThumbnailSizing.Dimensions wide = ThumbnailSizing.calculateDimensions(1920, 1080, 1);
        assertEquals(1, wide.width());
        assertEquals(1, wide.height());

        ThumbnailSizing.Dimensions tall = ThumbnailSizing.calculateDimensions(100, 500, 1);
        assertEquals(1, tall.width());
        assertEquals(5, tall.height());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 100, 320",
            "-1, 100, 320",
            "100, 0, 320",
            "100, -50, 320",
            "100, 100, 0",
            "100, 100, -10"
    })
    void testNonPositiveInputsThrowIllegalArgument(int srcWidth, int srcHeight, int maxTargetWidth) {
        assertThrows(IllegalArgumentException.class,
                () -> ThumbnailSizing.calculateDimensions(srcWidth, srcHeight, maxTargetWidth));
    }
}
