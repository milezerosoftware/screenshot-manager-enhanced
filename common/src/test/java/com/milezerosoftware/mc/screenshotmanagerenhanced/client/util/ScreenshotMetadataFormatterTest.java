package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataFormatter.InspectorLayout;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataFormatter.MetadataRow;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader.FileInfo;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader.ParsedMetadata;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScreenshotMetadataFormatterTest {

    // =====================================================
    // Minecraft Metadata Rows
    // =====================================================

    @Test
    void testBuildMinecraftRowsFullMetadataOrderAndFormatting() {
        ParsedMetadata metadata = new ParsedMetadata(
                "Survival Base",
                "Overworld",
                "Birch Forest",
                "x: 100, y: 64, z: -200",
                "Hard",
                "42.50 d",
                "3.10 d",
                "26.2",
                "Steve",
                "survival",
                "summary");

        List<MetadataRow> rows = ScreenshotMetadataFormatter.buildMinecraftRows(metadata);
        assertEquals(10, rows.size());

        assertEquals(List.of(
                "World", "Dimension", "Biome", "Coordinates", "Difficulty",
                "Days Played", "World Age", "MC Version", "Player", "Game Mode"),
                rows.stream().map(MetadataRow::label).toList());

        assertEquals("Survival Base", rows.get(0).value());
        assertEquals("x: 100, y: 64, z: -200", rows.get(3).value());
        assertTrue(rows.get(3).isCopyable());
        assertEquals("x: 100, y: 64, z: -200", rows.get(3).copyValue());
        assertFalse(rows.get(0).isCopyable());
        assertEquals("Survival", rows.get(9).value(), "Lowercase game mode should be capitalized");
    }

    @Test
    void testBuildMinecraftRowsOmitsNullBlankAndUnknownValues() {
        ParsedMetadata partial = new ParsedMetadata(
                "MENU",
                "Unknown",
                "   ",
                "x: 0, y: 0, z: 0",
                "UNKNOWN",
                "0.00 d",
                null,
                "1.21.11",
                "Unknown",
                null,
                null);

        List<MetadataRow> rows = ScreenshotMetadataFormatter.buildMinecraftRows(partial);
        assertEquals(List.of("World", "Coordinates", "Days Played", "MC Version"),
                rows.stream().map(MetadataRow::label).toList());
    }

    @Test
    void testBuildMinecraftRowsEmptyAndNull() {
        assertTrue(ScreenshotMetadataFormatter.buildMinecraftRows(null).isEmpty());
        assertTrue(ScreenshotMetadataFormatter.buildMinecraftRows(ParsedMetadata.EMPTY).isEmpty());
    }

    // =====================================================
    // File Info Rows, Size & Timestamp Formatting
    // =====================================================

    @Test
    void testBuildFileInfoRows() {
        long timestamp = Instant.parse("2026-04-15T14:30:00Z").toEpochMilli();
        FileInfo info = new FileInfo(1920, 1080, 1_572_864L, timestamp);

        List<MetadataRow> rows = ScreenshotMetadataFormatter.buildFileInfoRows(info, ZoneOffset.UTC);
        assertEquals(3, rows.size());
        assertEquals("Resolution", rows.get(0).label());
        assertEquals("1920 \u00D7 1080", rows.get(0).value());
        assertEquals("File Size", rows.get(1).label());
        assertEquals("1.50 MB", rows.get(1).value());
        assertEquals("Modified", rows.get(2).label());
        assertEquals("2026-04-15 14:30", rows.get(2).value());
    }

    @Test
    void testBuildFileInfoRowsHandlesMissingFields() {
        assertTrue(ScreenshotMetadataFormatter.buildFileInfoRows(null, ZoneOffset.UTC).isEmpty());
        assertTrue(ScreenshotMetadataFormatter.buildFileInfoRows(FileInfo.EMPTY, ZoneOffset.UTC).isEmpty());

        FileInfo sizeOnly = new FileInfo(0, 0, 512L, 0L);
        List<MetadataRow> rows = ScreenshotMetadataFormatter.buildFileInfoRows(sizeOnly, ZoneOffset.UTC);
        assertEquals(1, rows.size());
        assertEquals("File Size", rows.get(0).label());
        assertEquals("512 B", rows.get(0).value());
    }

    @Test
    void testFormatFileSize() {
        assertEquals("Unknown", ScreenshotMetadataFormatter.formatFileSize(-1));
        assertEquals("0 B", ScreenshotMetadataFormatter.formatFileSize(0));
        assertEquals("512 B", ScreenshotMetadataFormatter.formatFileSize(512));
        assertEquals("1023 B", ScreenshotMetadataFormatter.formatFileSize(1023));
        assertEquals("1.0 KB", ScreenshotMetadataFormatter.formatFileSize(1024));
        assertEquals("1.5 KB", ScreenshotMetadataFormatter.formatFileSize(1536));
        assertEquals("1.00 MB", ScreenshotMetadataFormatter.formatFileSize(1024L * 1024L));
        assertEquals("2.50 MB", ScreenshotMetadataFormatter.formatFileSize(2_621_440L));
        assertEquals("1.50 GB", ScreenshotMetadataFormatter.formatFileSize(1_610_612_736L));
    }

    @Test
    void testExtractCoordinateCopyText() {
        assertEquals("x: 12, y: 64, z: -34",
                ScreenshotMetadataFormatter.extractCoordinateCopyText("  x: 12, y: 64, z: -34  "));
        assertNull(ScreenshotMetadataFormatter.extractCoordinateCopyText((String) null));
        assertNull(ScreenshotMetadataFormatter.extractCoordinateCopyText("   "));
        assertNull(ScreenshotMetadataFormatter.extractCoordinateCopyText("Unknown"));
        assertNull(ScreenshotMetadataFormatter.extractCoordinateCopyText((ParsedMetadata) null));

        ParsedMetadata meta = new ParsedMetadata(
                "World", null, null, "x: 5, y: 6, z: 7",
                null, null, null, null, null, null, null);
        assertEquals("x: 5, y: 6, z: 7", ScreenshotMetadataFormatter.extractCoordinateCopyText(meta));
    }

    // =====================================================
    // Responsive Layout Calculations
    // =====================================================

    @Test
    void testComputeLayoutWhenClosed() {
        InspectorLayout layout = ScreenshotMetadataFormatter.computeLayout(1000, 600, false);
        assertFalse(layout.panelOpen());
        assertFalse(layout.overlayMode());
        assertEquals(0, layout.panelWidth());
        assertEquals(800, layout.maxImageWidth());
        assertEquals(390, layout.maxImageHeight());
    }

    @Test
    void testComputeLayoutSideBySideOnStandardAndLargeScreens() {
        // Large screen: panel width clamped to MAX_PANEL_WIDTH (220)
        InspectorLayout large = ScreenshotMetadataFormatter.computeLayout(1000, 600, true);
        assertTrue(large.panelOpen());
        assertFalse(large.overlayMode());
        assertEquals(ScreenshotMetadataFormatter.MAX_PANEL_WIDTH, large.panelWidth());
        assertTrue(large.leftPaneWidth() + large.panelWidth() < 1000);
        assertTrue(large.maxImageWidth() <= large.leftPaneWidth());

        // Medium screen (427x240): panel width clamped to MIN_PANEL_WIDTH (170), no overflow
        InspectorLayout medium = ScreenshotMetadataFormatter.computeLayout(427, 240, true);
        assertFalse(medium.overlayMode());
        assertEquals(ScreenshotMetadataFormatter.MIN_PANEL_WIDTH, medium.panelWidth());
        assertTrue(medium.leftPaneWidth() > 0);
        assertTrue(medium.maxImageWidth() <= medium.leftPaneWidth());
        assertEquals(427 - 170 - ScreenshotMetadataFormatter.PANEL_GAP - (ScreenshotMetadataFormatter.HORIZONTAL_MARGIN * 2),
                medium.leftPaneWidth());

        // Exact threshold (360px): still side-by-side
        InspectorLayout threshold = ScreenshotMetadataFormatter.computeLayout(360, 240, true);
        assertFalse(threshold.overlayMode());
        assertEquals(ScreenshotMetadataFormatter.MIN_PANEL_WIDTH, threshold.panelWidth());
        assertTrue(threshold.maxImageWidth() > 0);
        assertTrue(threshold.maxImageWidth() <= threshold.leftPaneWidth());
    }

    @Test
    void testComputeLayoutOverlayFallbackOnNarrowScreens() {
        // Below 360px triggers overlay mode
        InspectorLayout narrow = ScreenshotMetadataFormatter.computeLayout(320, 240, true);
        assertTrue(narrow.panelOpen());
        assertTrue(narrow.overlayMode());
        assertTrue(narrow.panelWidth() >= ScreenshotMetadataFormatter.MIN_PANEL_WIDTH);
        assertTrue(narrow.panelWidth() <= ScreenshotMetadataFormatter.MAX_PANEL_WIDTH);
        assertEquals((int) (320 * 0.80), narrow.maxImageWidth());

        // Very narrow screen (150px): panel never exceeds viewport width
        InspectorLayout tiny = ScreenshotMetadataFormatter.computeLayout(150, 120, true);
        assertTrue(tiny.overlayMode());
        assertTrue(tiny.panelWidth() > 0 && tiny.panelWidth() < 150);
    }

    @Test
    void testComputeFooterWidth() {
        assertEquals(315, ScreenshotMetadataFormatter.computeFooterWidth(427, 250, 315));
        assertEquals(500, ScreenshotMetadataFormatter.computeFooterWidth(800, 500, 315));
        assertEquals(784, ScreenshotMetadataFormatter.computeFooterWidth(800, 900, 315));
    }
}
