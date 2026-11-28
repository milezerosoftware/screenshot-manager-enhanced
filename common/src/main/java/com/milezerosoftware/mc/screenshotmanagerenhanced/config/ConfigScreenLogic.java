package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.FilePickerHelper;

/**
 * Pure-Java configuration screen logic for validation, preview formatting,
 * and save-state transitions. Kept free of Minecraft/YACL classes so it can
 * be unit-tested directly under {@code :common:test}.
 */
public final class ConfigScreenLogic {

    private static final String SAMPLE_WORLD = "My_Survival_World";
    private static final String SAMPLE_DIMENSION = "the_nether";
    private static final String SAMPLE_DATE = "2026-01-30";
    private static final String SAMPLE_FILENAME = "2026-01-30_14.22.05.png";
    private static final String DEFAULT_BASE_PATH = "screenshots";

    private ConfigScreenLogic() {
    }

    /**
     * Outcome of applying pending config screen edits to {@link ModConfig}.
     *
     * @param needsWarningDialog true if the user enabled custom save path without
     *                           having acknowledged the conflict warning yet
     * @param customPathInvalid  true if the user attempted to enable custom save
     *                           path with an invalid directory
     * @param validationError    human-readable validation error when
     *                           {@code customPathInvalid} is true, otherwise null
     */
    public record SaveOutcome(
            boolean needsWarningDialog,
            boolean customPathInvalid,
            String validationError) {
    }

    /**
     * Returns true when custom save path is enabled in the loaded config but
     * the user has not yet acknowledged the world-name conflict warning.
     */
    public static boolean needsEntryWarning(ModConfig config) {
        return config != null
                && config.customSavePathEnabled
                && config.customPathConfig != null
                && !config.customPathConfig.hasAcknowledgedWarning;
    }

    /**
     * Builds a clean example screenshot path for the given {@link GroupingMode}
     * and base directory.
     */
    public static String buildExamplePath(GroupingMode mode, String basePath) {
        String normalizedBase = normalizeBasePath(basePath);
        GroupingMode effectiveMode = mode != null ? mode : GroupingMode.WORLD;

        return switch (effectiveMode) {
            case WORLD -> normalizedBase + "/" + SAMPLE_WORLD + "/" + SAMPLE_FILENAME;
            case DATE -> normalizedBase + "/" + SAMPLE_DATE + "/" + SAMPLE_FILENAME;
            case WORLD_DIMENSION -> normalizedBase + "/" + SAMPLE_WORLD + "/" + SAMPLE_DIMENSION
                    + "/" + SAMPLE_FILENAME;
            case WORLD_DATE -> normalizedBase + "/" + SAMPLE_WORLD + "/" + SAMPLE_DATE
                    + "/" + SAMPLE_FILENAME;
            case WORLD_DIMENSION_DATE -> normalizedBase + "/" + SAMPLE_WORLD + "/" + SAMPLE_DIMENSION
                    + "/" + SAMPLE_DATE + "/" + SAMPLE_FILENAME;
            case WORLD_DATE_DIMENSION -> normalizedBase + "/" + SAMPLE_WORLD + "/" + SAMPLE_DATE
                    + "/" + SAMPLE_DIMENSION + "/" + SAMPLE_FILENAME;
            case NONE -> normalizedBase + "/" + SAMPLE_FILENAME;
        };
    }

    /**
     * Returns a formatted plain-text guide covering all 7 {@link GroupingMode}
     * values with example paths.
     */
    public static String buildGroupingGuideText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Grouping Modes organize screenshots into folders:\n\n");
        appendGuideEntry(sb, GroupingMode.WORLD, "Groups by world/server name");
        appendGuideEntry(sb, GroupingMode.DATE, "Groups by date (yyyy-MM-dd)");
        appendGuideEntry(sb, GroupingMode.WORLD_DIMENSION, "Groups by world, then dimension");
        appendGuideEntry(sb, GroupingMode.WORLD_DATE, "Groups by world, then date");
        appendGuideEntry(sb, GroupingMode.WORLD_DIMENSION_DATE, "Groups by world, dimension, then date");
        appendGuideEntry(sb, GroupingMode.WORLD_DATE_DIMENSION, "Groups by world, date, then dimension");
        appendGuideEntry(sb, GroupingMode.NONE, "Standard Minecraft behavior (no subfolders)");
        return sb.toString().trim();
    }

    /**
     * Applies pending config screen values to {@code config} with directory
     * validation and custom-path warning safety.
     *
     * @param config                 target configuration to mutate
     * @param groupingMode           selected grouping mode
     * @param embedMetadata          whether to embed XMP metadata into PNGs
     * @param customSavePathEnabled  whether the user wants custom save path enabled
     * @param customPath             user-entered custom directory path
     * @param defaultScreenshotsPath fallback default screenshots directory path
     * @return {@link SaveOutcome} indicating whether a validation toast or warning
     *         confirmation dialog is needed
     */
    public static SaveOutcome applySaveState(
            ModConfig config,
            GroupingMode groupingMode,
            boolean embedMetadata,
            boolean customSavePathEnabled,
            String customPath,
            String defaultScreenshotsPath) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (config.customPathConfig == null) {
            config.customPathConfig = new CustomPathConfig();
        }

        config.groupingMode = groupingMode != null ? groupingMode : GroupingMode.WORLD;
        config.embedMetadata = embedMetadata;

        String trimmedPath = customPath == null ? "" : customPath.trim();
        String trimmedDefault = defaultScreenshotsPath == null ? "" : defaultScreenshotsPath.trim();

        boolean needsWarningDialog = false;
        boolean customPathInvalid = false;
        String validationError = null;

        if (customSavePathEnabled) {
            String effectivePath = trimmedPath.isEmpty() ? trimmedDefault : trimmedPath;
            FilePickerHelper.ValidationResult validation = FilePickerHelper.validateDirectory(effectivePath);

            if (!validation.isValid) {
                config.customSavePathEnabled = false;
                customPathInvalid = true;
                validationError = validation.errorMessage;
            } else {
                config.customPathConfig.customPath = effectivePath;
                if (!config.customPathConfig.hasAcknowledgedWarning) {
                    // Keep disabled until the user explicitly acknowledges the warning dialog
                    config.customSavePathEnabled = false;
                    needsWarningDialog = true;
                } else {
                    config.customSavePathEnabled = true;
                }
            }
        } else {
            config.customSavePathEnabled = false;
            String existingPath = config.customPathConfig.customPath != null
                    ? config.customPathConfig.customPath
                    : "";
            if (!trimmedPath.isEmpty() && !trimmedPath.equals(existingPath)) {
                FilePickerHelper.ValidationResult validation = FilePickerHelper.validateDirectory(trimmedPath);
                if (validation.isValid) {
                    config.customPathConfig.customPath = trimmedPath;
                }
            }
        }

        config.advancedSettings = config.customSavePathEnabled || config.embedMetadata;
        return new SaveOutcome(needsWarningDialog, customPathInvalid, validationError);
    }

    private static void appendGuideEntry(StringBuilder sb, GroupingMode mode, String description) {
        sb.append(mode.name()).append(" - ").append(description).append("\n")
                .append("  Example: ").append(buildExamplePath(mode, DEFAULT_BASE_PATH)).append("\n\n");
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return DEFAULT_BASE_PATH;
        }
        String trimmed = basePath.trim();
        while (trimmed.length() > 1 && (trimmed.endsWith("/") || trimmed.endsWith("\\"))) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
