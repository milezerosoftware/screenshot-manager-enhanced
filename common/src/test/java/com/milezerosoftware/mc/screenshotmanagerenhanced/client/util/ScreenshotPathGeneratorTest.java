package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.config.GroupingMode;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.WorldConfig;
import org.junit.jupiter.api.Test;

import java.io.File;
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
}
