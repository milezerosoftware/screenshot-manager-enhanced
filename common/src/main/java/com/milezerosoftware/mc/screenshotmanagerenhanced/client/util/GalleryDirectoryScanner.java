package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Pure-Java helper for scanning screenshot gallery directories in O(N) filesystem calls,
 * computing pagination slices, resolving the active gallery root directory, and fitting
 * images to bounds while preserving aspect ratio.
 */
public final class GalleryDirectoryScanner {

    public record ImageEntry(Path path, long lastModifiedMillis) {
    }

    public record ScanResult(List<Path> directories, List<ImageEntry> images) {
        public List<Path> imagePaths() {
            return images.stream().map(ImageEntry::path).toList();
        }
    }

    private static final Comparator<Path> DIRECTORY_COMPARATOR =
            Comparator.comparing((Path p) -> fileNameString(p), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(GalleryDirectoryScanner::fileNameString);

    private static final Comparator<ImageEntry> IMAGE_NEWEST_FIRST_COMPARATOR =
            Comparator.comparingLong(ImageEntry::lastModifiedMillis).reversed()
                    .thenComparing(e -> fileNameString(e.path()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(e -> fileNameString(e.path()));

    private GalleryDirectoryScanner() {
    }

    /**
     * Scans a directory in O(N) syscalls by reading {@link BasicFileAttributes} once per entry.
     * Directories are sorted case-insensitively by name; PNG images are sorted newest-first in memory.
     */
    public static ScanResult scan(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        List<Path> dirs = new ArrayList<>();
        List<ImageEntry> images = new ArrayList<>();

        try (Stream<Path> stream = Files.list(directory)) {
            for (Path entry : stream.toList()) {
                BasicFileAttributes attrs;
                try {
                    attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                } catch (IOException ignored) {
                    // Entry may have been deleted or become inaccessible mid-scan; skip gracefully.
                    continue;
                }

                if (attrs.isDirectory()) {
                    dirs.add(entry);
                } else if (attrs.isRegularFile() && isPng(entry)) {
                    images.add(new ImageEntry(entry, attrs.lastModifiedTime().toMillis()));
                }
            }
        }

        dirs.sort(DIRECTORY_COMPARATOR);
        images.sort(IMAGE_NEWEST_FIRST_COMPARATOR);

        return new ScanResult(List.copyOf(dirs), List.copyOf(images));
    }

    /**
     * Returns the recommended page size for a given grid column count.
     * Always returns an exact multiple of {@code columns} (3 -> 24, 6 -> 36, 8 -> 48).
     */
    public static int pageSizeForColumns(int columns) {
        int safeCols = Math.max(1, columns);
        if (safeCols <= 3) {
            return safeCols * 8;
        }
        return safeCols * 6;
    }

    /**
     * Returns the total number of pages for {@code totalItems} at {@code pageSize} items per page.
     * Always returns at least 1 so empty directories render as "Page 1 / 1".
     */
    public static int getTotalPages(int totalItems, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        if (totalItems <= 0) {
            return 1;
        }
        return (int) (((long) totalItems + pageSize - 1) / pageSize);
    }

    /**
     * Clamps a 0-based page index into the valid range {@code [0, getTotalPages - 1]}.
     */
    public static int clampPage(int page, int totalItems, int pageSize) {
        int maxPage = getTotalPages(totalItems, pageSize) - 1;
        if (page < 0) {
            return 0;
        }
        return Math.min(page, maxPage);
    }

    /**
     * Returns an unmodifiable slice of {@code items} corresponding to the clamped 0-based {@code page}.
     */
    public static <T> List<T> getPageSlice(List<T> items, int page, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int clamped = clampPage(page, items.size(), pageSize);
        int start = clamped * pageSize;
        int end = Math.min(start + pageSize, items.size());
        return List.copyOf(items.subList(start, end));
    }

    /**
     * Resolves the gallery root directory, honoring an active, acknowledged, existing absolute
     * custom save path when configured, and falling back to {@code defaultScreenshotsDir}.
     */
    public static Path resolveRootDirectory(Path defaultScreenshotsDir, ModConfig config) {
        Objects.requireNonNull(defaultScreenshotsDir, "defaultScreenshotsDir");
        if (config != null
                && config.customSavePathEnabled
                && config.customPathConfig != null
                && config.customPathConfig.hasAcknowledgedWarning
                && config.customPathConfig.customPath != null
                && !config.customPathConfig.customPath.isEmpty()) {
            try {
                Path customPath = Path.of(config.customPathConfig.customPath);
                if (customPath.isAbsolute() && Files.isDirectory(customPath)) {
                    return customPath.normalize();
                }
            } catch (Exception ignored) {
                // Invalid path syntax falls back to default screenshots directory
            }
        }
        return defaultScreenshotsDir.normalize();
    }

    /**
     * Computes {@code [width, height]} that fits an image of {@code srcWidth x srcHeight} inside
     * {@code maxWidth x maxHeight} while preserving its exact aspect ratio without stretching.
     */
    public static int[] fitWithinBounds(int srcWidth, int srcHeight, int maxWidth, int maxHeight) {
        int safeMaxW = Math.max(1, maxWidth);
        int safeMaxH = Math.max(1, maxHeight);
        if (srcWidth <= 0 || srcHeight <= 0) {
            return new int[]{safeMaxW, safeMaxH};
        }
        double scale = Math.min((double) safeMaxW / srcWidth, (double) safeMaxH / srcHeight);
        int fittedW = Math.max(1, Math.min(safeMaxW, (int) Math.round(srcWidth * scale)));
        int fittedH = Math.max(1, Math.min(safeMaxH, (int) Math.round(srcHeight * scale)));
        return new int[]{fittedW, fittedH};
    }

    private static boolean isPng(Path path) {
        return fileNameString(path).toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private static String fileNameString(Path path) {
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : path.toString();
    }
}
