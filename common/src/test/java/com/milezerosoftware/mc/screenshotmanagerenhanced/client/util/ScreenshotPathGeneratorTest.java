package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.config.CustomPathConfig;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.GroupingMode;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.WorldConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScreenshotPathGeneratorTest {

    // Simulating the passed 'screenshots' directory
    private final File screenshotsDir = new File("run/screenshots");
    private final String rawWorldId = "Test World 1";
    private final String safeWorldId = "Test_World_1";
    private final String dimension = "minecraft:the_nether";
    private final Date date = new GregorianCalendar(2025, Calendar.JANUARY, 1).getTime();

    @TempDir
    Path tempDir;

    @Test
    public void testDefaultGlobal() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD; // Default

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        assertEquals(new File(screenshotsDir, "Test_World_1"), result);
    }

    @Test
    public void testDateMode() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.DATE;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        assertEquals(new File(screenshotsDir, "2025-01-01"), result);
    }

    @Test
    public void testWorldDimensionDate() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD_DIMENSION_DATE;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        assertEquals(new File(screenshotsDir, "Test_World_1/minecraft_the_nether/2025-01-01"), result);
    }

    @Test
    public void testWorldDimension() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD_DIMENSION;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        assertEquals(new File(screenshotsDir, "Test_World_1/minecraft_the_nether"), result);
    }

    @Test
    public void testWorldDate() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD_DATE;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        assertEquals(new File(screenshotsDir, "Test_World_1/2025-01-01"), result);
    }

    @Test
    public void testWorldDateDimension() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD_DATE_DIMENSION;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        assertEquals(new File(screenshotsDir, "Test_World_1/2025-01-01/minecraft_the_nether"), result);
    }

    @Test
    public void testPerWorldDisabled() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD;
        // Disable mod for this world
        WorldConfig worldConfig = new WorldConfig();
        worldConfig.active = false;
        config.worldRules.put(rawWorldId, worldConfig);

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        // Should fall back to vanilla behavior (return the input dir directly)
        assertEquals(screenshotsDir, result);
    }

    // --- Custom Path Tests ---

    @Test
    public void testCustomPathEnabledAndAcknowledged() {
        // Use temp directory which actually exists
        File customDir = tempDir.toFile();

        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD;
        config.customSavePathEnabled = true;
        config.customPathConfig = new CustomPathConfig(true, customDir.getAbsolutePath());

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        // Should use custom path as base, then apply grouping
        assertEquals(new File(customDir, "Test_World_1"), result);
    }

    @Test
    public void testCustomPathEnabledNotAcknowledged() {
        File customDir = tempDir.toFile();

        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD;
        config.customSavePathEnabled = true;
        config.customPathConfig = new CustomPathConfig(false, customDir.getAbsolutePath()); // NOT acknowledged

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        // Should use default path because warning not acknowledged
        assertEquals(new File(screenshotsDir, "Test_World_1"), result);
    }

    @Test
    public void testCustomPathDisabled() {
        File customDir = tempDir.toFile();

        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD;
        config.customSavePathEnabled = false; // Disabled
        config.customPathConfig = new CustomPathConfig(true, customDir.getAbsolutePath());

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        // Should use default path because feature is disabled
        assertEquals(new File(screenshotsDir, "Test_World_1"), result);
    }

    @Test
    public void testCustomPathWithGrouping() {
        File customDir = tempDir.toFile();

        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD_DIMENSION_DATE;
        config.customSavePathEnabled = true;
        config.customPathConfig = new CustomPathConfig(true, customDir.getAbsolutePath());

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        // Grouping should build on top of custom path
        assertEquals(new File(customDir, "Test_World_1/minecraft_the_nether/2025-01-01"), result);
    }

    @Test
    public void testCustomPathEmpty() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD;
        config.customSavePathEnabled = true;
        config.customPathConfig = new CustomPathConfig(true, ""); // Empty path

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        // Should use default path because custom path is empty
        assertEquals(new File(screenshotsDir, "Test_World_1"), result);
    }

    @Test
    public void testCustomPathInvalidDirectory() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD;
        config.customSavePathEnabled = true;
        // Path that doesn't exist
        config.customPathConfig = new CustomPathConfig(true, "/this/path/definitely/does/not/exist/anywhere");

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        // Should use default path because custom path doesn't exist
        assertEquals(new File(screenshotsDir, "Test_World_1"), result);
    }

    // --- NONE Mode Tests ---

    @Test
    public void testNoneMode() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.NONE;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        assertEquals(screenshotsDir, result);
    }

    @Test
    public void testNoneModeWithCustomPath() {
        File customDir = tempDir.toFile();

        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.NONE;
        config.customSavePathEnabled = true;
        config.customPathConfig = new CustomPathConfig(true, customDir.getAbsolutePath());

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, date);
        // NONE with custom path should return the custom dir with no subfolders
        assertEquals(customDir, result);
    }

    // --- Null Date Tests (Gallery Browsing) ---

    @Test
    public void testDateModeWithNullDate() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.DATE;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, null);
        // With null date, DATE mode should return the base screenshots dir
        assertEquals(screenshotsDir, result);
    }

    @Test
    public void testWorldDateWithNullDate() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD_DATE;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, null);
        // With null date, should fall back to world dir only
        assertEquals(new File(screenshotsDir, "Test_World_1"), result);
    }

    @Test
    public void testWorldDimensionDateWithNullDate() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD_DIMENSION_DATE;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, null);
        // With null date, should fall back to world/dimension
        assertEquals(new File(new File(screenshotsDir, "Test_World_1"), "minecraft_the_nether"), result);
    }

    @Test
    public void testWorldDateDimensionWithNullDate() {
        ModConfig config = new ModConfig();
        config.groupingMode = GroupingMode.WORLD_DATE_DIMENSION;

        File result = ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId,
                dimension, null);
        // With null date, should fall back to world dir only (date level is skipped)
        assertEquals(new File(screenshotsDir, "Test_World_1"), result);
    }
}
