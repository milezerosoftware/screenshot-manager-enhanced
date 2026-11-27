package com.milezerosoftware.mc.screenshotmanagerenhanced.client.render;

/**
 * Pure-Java aspect-ratio-preserving thumbnail dimension calculator.
 * <p>
 * Computes target dimensions that fit within a maximum width without ever
 * upscaling the source image and while guaranteeing at least 1x1 output.
 */
public final class ThumbnailSizing {

    public record Dimensions(int width, int height) {
    }

    private ThumbnailSizing() {
    }

    /**
     * Computes aspect-ratio-preserving thumbnail dimensions for a source image.
     *
     * @param srcWidth       source image width in pixels (must be &gt; 0)
     * @param srcHeight      source image height in pixels (must be &gt; 0)
     * @param maxTargetWidth maximum desired output width in pixels (must be &gt; 0)
     * @return computed dimensions where {@code 1 <= outWidth <= srcWidth} and
     *         {@code 1 <= outHeight <= srcHeight}
     * @throws IllegalArgumentException if any dimension or target width is &lt;= 0
     */
    public static Dimensions calculateDimensions(int srcWidth, int srcHeight, int maxTargetWidth) {
        if (srcWidth <= 0 || srcHeight <= 0 || maxTargetWidth <= 0) {
            throw new IllegalArgumentException(
                    "srcWidth, srcHeight, and maxTargetWidth must all be positive (got "
                            + srcWidth + "x" + srcHeight + ", maxTargetWidth=" + maxTargetWidth + ")");
        }

        if (srcWidth <= maxTargetWidth) {
            return new Dimensions(srcWidth, srcHeight);
        }

        int outWidth = maxTargetWidth;
        long scaledHeight = Math.round(((double) srcHeight * (double) maxTargetWidth) / (double) srcWidth);
        int outHeight = (int) Math.max(1L, Math.min(srcHeight, scaledHeight));
        return new Dimensions(outWidth, outHeight);
    }
}
