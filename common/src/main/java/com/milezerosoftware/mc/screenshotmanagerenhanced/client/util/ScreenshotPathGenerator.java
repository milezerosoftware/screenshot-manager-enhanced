package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.config.GroupingMode;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.WorldConfig;

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
     * @param date           The date of the screenshot. Can be null for Gallery
     *                       browsing.
     * @return The target directory for the screenshot.
     */
    public static File getScreenshotDirectory(File screenshotsDir, ModConfig config,
            String rawWorldId, String safeWorldId, String dimension, Date date) {
        // Check for global custom path override (requires enabled + acknowledged +
        // valid path)
        if (config.customSavePathEnabled &&
                config.customPathConfig != null &&
                config.customPathConfig.hasAcknowledgedWarning &&
                !config.customPathConfig.customPath.isEmpty()) {
            File customDir = new File(config.customPathConfig.customPath);
            if (customDir.isAbsolute() && customDir.isDirectory()) {
                screenshotsDir = customDir; // Use custom path as new base
            }
        }

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

        String dateStr = null;
        if (date != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            dateStr = dateFormat.format(date);
        }

        // Sanitize dimension if needed, usually passed as "minecraft:overworld"
        String safeDimension = dimension.replace(":", "_");

        return switch (mode) {
            case DATE -> (dateStr != null) ? new File(screenshotsDir, dateStr) : screenshotsDir;
            case WORLD -> new File(screenshotsDir, safeWorldId);
            case WORLD_DIMENSION -> new File(new File(screenshotsDir, safeWorldId), safeDimension);
            case WORLD_DATE -> (dateStr != null)
                    ? new File(new File(screenshotsDir, safeWorldId), dateStr)
                    : new File(screenshotsDir, safeWorldId);
            case WORLD_DIMENSION_DATE -> {
                File dimDir = new File(new File(screenshotsDir, safeWorldId), safeDimension);
                yield (dateStr != null) ? new File(dimDir, dateStr) : dimDir;
            }
            case WORLD_DATE_DIMENSION -> {
                // Hierarchy: World -> Date -> Dimension
                // If date is null, we return World (so user sees date folders)
                yield (dateStr != null)
                        ? new File(new File(new File(screenshotsDir, safeWorldId), dateStr), safeDimension)
                        : new File(screenshotsDir, safeWorldId);
            }
            case NONE -> screenshotsDir;
        };
    }
}
