package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.config.CustomPathConfig;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalleryDirectoryScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scanSortsDirectoriesCaseInsensitivelyAndImagesNewestFirst() throws IOException {
        Path dirZ = Files.createDirectory(tempDir.resolve("zebra"));
        Path dirA = Files.createDirectory(tempDir.resolve("Alpha"));
        Path dirB = Files.createDirectory(tempDir.resolve("beta"));

        Path oldPng = Files.writeString(tempDir.resolve("old.png"), "old");
        Path midPng = Files.writeString(tempDir.resolve("mid.PNG"), "mid");
        Path newPng = Files.writeString(tempDir.resolve("new.png"), "new");
        Files.writeString(tempDir.resolve("notes.txt"), "ignored");
        Files.writeString(tempDir.resolve("photo.jpg"), "ignored");

        Files.setLastModifiedTime(oldPng, FileTime.from(Instant.ofEpochMilli(1_000L)));
        Files.setLastModifiedTime(midPng, FileTime.from(Instant.ofEpochMilli(2_000L)));
        Files.setLastModifiedTime(newPng, FileTime.from(Instant.ofEpochMilli(3_000L)));

        GalleryDirectoryScanner.ScanResult result = GalleryDirectoryScanner.scan(tempDir);

        assertEquals(List.of(dirA, dirB, dirZ), result.directories());
        assertEquals(3, result.images().size());
        assertEquals(List.of(newPng, midPng, oldPng), result.imagePaths());
        assertEquals(3_000L, result.images().get(0).lastModifiedMillis());
        assertEquals(2_000L, result.images().get(1).lastModifiedMillis());
        assertEquals(1_000L, result.images().get(2).lastModifiedMillis());
    }

    @Test
    void scanHandlesEmptyDirectory() throws IOException {
        GalleryDirectoryScanner.ScanResult result = GalleryDirectoryScanner.scan(tempDir);
        assertTrue(result.directories().isEmpty());
        assertTrue(result.images().isEmpty());
        assertTrue(result.imagePaths().isEmpty());
    }

    @Test
    void pageSizeForColumnsReturnsMultiplesOfColumns() {
        assertEquals(24, GalleryDirectoryScanner.pageSizeForColumns(3));
        assertEquals(36, GalleryDirectoryScanner.pageSizeForColumns(6));
        assertEquals(48, GalleryDirectoryScanner.pageSizeForColumns(8));
        assertEquals(8, GalleryDirectoryScanner.pageSizeForColumns(0));
    }

    @Test
    void totalPagesAndClampPageHandleBoundaries() {
        assertEquals(1, GalleryDirectoryScanner.getTotalPages(0, 24));
        assertEquals(1, GalleryDirectoryScanner.getTotalPages(1, 24));
        assertEquals(1, GalleryDirectoryScanner.getTotalPages(24, 24));
        assertEquals(2, GalleryDirectoryScanner.getTotalPages(25, 24));
        assertEquals(3, GalleryDirectoryScanner.getTotalPages(50, 24));

        assertEquals(0, GalleryDirectoryScanner.clampPage(-5, 50, 24));
        assertEquals(0, GalleryDirectoryScanner.clampPage(0, 0, 24));
        assertEquals(2, GalleryDirectoryScanner.clampPage(99, 50, 24));
        assertEquals(1, GalleryDirectoryScanner.clampPage(1, 50, 24));

        assertThrows(IllegalArgumentException.class, () -> GalleryDirectoryScanner.getTotalPages(10, 0));
        assertThrows(IllegalArgumentException.class, () -> GalleryDirectoryScanner.getPageSlice(List.of("a"), 0, 0));
    }

    @Test
    void getPageSliceReturnsExpectedItems() {
        List<Integer> items = List.of(0, 1, 2, 3, 4, 5, 6);
        assertEquals(List.of(0, 1, 2), GalleryDirectoryScanner.getPageSlice(items, 0, 3));
        assertEquals(List.of(3, 4, 5), GalleryDirectoryScanner.getPageSlice(items, 1, 3));
        assertEquals(List.of(6), GalleryDirectoryScanner.getPageSlice(items, 2, 3));
        assertEquals(List.of(6), GalleryDirectoryScanner.getPageSlice(items, 10, 3));
        assertEquals(List.of(0, 1, 2), GalleryDirectoryScanner.getPageSlice(items, -1, 3));
        assertEquals(List.of(), GalleryDirectoryScanner.getPageSlice(List.of(), 0, 3));
        assertEquals(List.of(), GalleryDirectoryScanner.getPageSlice(null, 0, 3));
    }

    @Test
    void resolveRootDirectoryUsesCustomPathOnlyWhenEnabledAcknowledgedAndExisting() throws IOException {
        Path defaultDir = tempDir.resolve("screenshots");
        Path customDir = Files.createDirectory(tempDir.resolve("custom_shots"));

        ModConfig config = new ModConfig();
        config.customSavePathEnabled = true;
        config.customPathConfig = new CustomPathConfig(true, customDir.toAbsolutePath().toString());
        assertEquals(customDir.normalize(), GalleryDirectoryScanner.resolveRootDirectory(defaultDir, config));

        // Disabled -> fallback
        config.customSavePathEnabled = false;
        assertEquals(defaultDir.normalize(), GalleryDirectoryScanner.resolveRootDirectory(defaultDir, config));

        // Not acknowledged -> fallback
        config.customSavePathEnabled = true;
        config.customPathConfig.hasAcknowledgedWarning = false;
        assertEquals(defaultDir.normalize(), GalleryDirectoryScanner.resolveRootDirectory(defaultDir, config));

        // Non-existent path -> fallback
        config.customPathConfig.hasAcknowledgedWarning = true;
        config.customPathConfig.customPath = tempDir.resolve("does_not_exist").toAbsolutePath().toString();
        assertEquals(defaultDir.normalize(), GalleryDirectoryScanner.resolveRootDirectory(defaultDir, config));

        // Relative path -> fallback
        config.customPathConfig.customPath = "relative/path";
        assertEquals(defaultDir.normalize(), GalleryDirectoryScanner.resolveRootDirectory(defaultDir, config));

        // Null config -> fallback
        assertEquals(defaultDir.normalize(), GalleryDirectoryScanner.resolveRootDirectory(defaultDir, null));
    }

    @Test
    void fitWithinBoundsPreservesAspectRatioWithoutStretching() {
        // 16:9 image into 160x90 box -> exact 160x90
        assertArrayEquals(new int[]{160, 90}, GalleryDirectoryScanner.fitWithinBounds(1920, 1080, 160, 90));
        // Ultrawide 21:9 (2560x1080) into 160x90 box -> width-constrained (160x68)
        assertArrayEquals(new int[]{160, 68}, GalleryDirectoryScanner.fitWithinBounds(2560, 1080, 160, 90));
        // Portrait 9:16 (1080x1920) into 160x90 box -> height-constrained (51x90)
        assertArrayEquals(new int[]{51, 90}, GalleryDirectoryScanner.fitWithinBounds(1080, 1920, 160, 90));
        // Square 1000x1000 into 160x90 box -> height-constrained (90x90)
        assertArrayEquals(new int[]{90, 90}, GalleryDirectoryScanner.fitWithinBounds(1000, 1000, 160, 90));
        // Invalid source dimensions -> returns safe bounds
        assertArrayEquals(new int[]{160, 90}, GalleryDirectoryScanner.fitWithinBounds(0, 0, 160, 90));
    }
}
