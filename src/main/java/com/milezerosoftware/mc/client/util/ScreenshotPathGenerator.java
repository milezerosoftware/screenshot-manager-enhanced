package com.milezerosoftware.mc.client.util;

import com.milezerosoftware.mc.config.GroupingMode;
import com.milezerosoftware.mc.config.ModConfig;
import com.milezerosoftware.mc.config.WorldConfig;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenshotPathGenerator {

    /**
     * resolving the screenshot file path based on the grouping mode and per-world
     * configuration.
     *
     * @param screenshotsDir The screenshots directory (e.g. run/screenshots).
     * @param config         The mod configuration.
     * @param rawWorldId     The raw world ID (for config lookup).
     * @param safeWorldId    The sanitized world ID (for directory creation).
     * @param dimension      The dimension name.
     * @param date           The date of the screenshot.
     * @return The target directory for the screenshot.
     */
    public static File getScreenshotDirectory(File screenshotsDir, ModConfig config,
            String rawWorldId, String safeWorldId, String dimension, Date date) {
        // 1. Check for Per-World Config
        WorldConfig worldConfig = config.worldRules.get(rawWorldId);

        // Default to global settings
        GroupingMode mode = config.groupingMode;

        if (worldConfig != null) {
            // If the mod is disabled for this world, use vanilla behavior (return the input
            // dir directly)
            if (!worldConfig.active) {
                return screenshotsDir;
            }
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String dateStr = dateFormat.format(date);

        // Sanitize dimension if needed, usually passed as "minecraft:overworld"
        String safeDimension = dimension.replace(":", "_");

        return switch (mode) {
            case DATE -> new File(screenshotsDir, dateStr);
            case WORLD, PROJECT -> new File(screenshotsDir, safeWorldId);
            case WORLD_DIMENSION -> new File(new File(screenshotsDir, safeWorldId), safeDimension);
            case WORLD_DATE -> new File(new File(screenshotsDir, safeWorldId), dateStr);
            case WORLD_DIMENSION_DATE ->
                new File(new File(new File(screenshotsDir, safeWorldId), safeDimension), dateStr);
            case WORLD_DATE_DIMENSION ->
                new File(new File(new File(screenshotsDir, safeWorldId), dateStr), safeDimension);
            case NONE -> screenshotsDir;
        };
    }
}
