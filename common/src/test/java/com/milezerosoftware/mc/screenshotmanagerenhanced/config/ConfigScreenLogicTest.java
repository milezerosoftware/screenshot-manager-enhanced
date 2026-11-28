package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigScreenLogicTest {

    @TempDir
    Path tempDir;

    // --- needsEntryWarning ---

    @Test
    void needsEntryWarningReturnsTrueWhenEnabledAndUnacknowledged() {
        ModConfig config = new ModConfig();
        config.customSavePathEnabled = true;
        config.customPathConfig.hasAcknowledgedWarning = false;

        assertTrue(ConfigScreenLogic.needsEntryWarning(config));
    }

    @Test
    void needsEntryWarningReturnsFalseWhenAcknowledgedOrDisabledOrNull() {
        assertFalse(ConfigScreenLogic.needsEntryWarning(null));

        ModConfig disabled = new ModConfig();
        disabled.customSavePathEnabled = false;
        disabled.customPathConfig.hasAcknowledgedWarning = false;
        assertFalse(ConfigScreenLogic.needsEntryWarning(disabled));

        ModConfig acknowledged = new ModConfig();
        acknowledged.customSavePathEnabled = true;
        acknowledged.customPathConfig.hasAcknowledgedWarning = true;
        assertFalse(ConfigScreenLogic.needsEntryWarning(acknowledged));

        ModConfig nullCustomConfig = new ModConfig();
        nullCustomConfig.customSavePathEnabled = true;
        nullCustomConfig.customPathConfig = null;
        assertFalse(ConfigScreenLogic.needsEntryWarning(nullCustomConfig));
    }

    // --- buildExamplePath & buildExampleFolder & getGroupingModeDescription ---

    @Test
    void buildExamplePathCoversAllSevenGroupingModes() {
        String base = "/tmp/screenshots";

        assertEquals("/tmp/screenshots/My_World/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.WORLD, base));
        assertEquals("/tmp/screenshots/2026-01-30/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.DATE, base));
        assertEquals("/tmp/screenshots/My_World/the_nether/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.WORLD_DIMENSION, base));
        assertEquals("/tmp/screenshots/My_World/2026-01-30/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.WORLD_DATE, base));
        assertEquals("/tmp/screenshots/My_World/the_nether/2026-01-30/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.WORLD_DIMENSION_DATE, base));
        assertEquals("/tmp/screenshots/My_World/2026-01-30/the_nether/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.WORLD_DATE_DIMENSION, base));
        assertEquals("/tmp/screenshots/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.NONE, base));
    }

    @Test
    void buildExampleFolderAndDescriptionCoverAllSevenGroupingModes() {
        assertEquals("screenshots/My_World/",
                ConfigScreenLogic.buildExampleFolder(GroupingMode.WORLD, "screenshots"));
        assertEquals("screenshots/2026-01-30/",
                ConfigScreenLogic.buildExampleFolder(GroupingMode.DATE, "screenshots"));
        assertEquals("screenshots/My_World/the_nether/",
                ConfigScreenLogic.buildExampleFolder(GroupingMode.WORLD_DIMENSION, "screenshots"));
        assertEquals("screenshots/My_World/2026-01-30/",
                ConfigScreenLogic.buildExampleFolder(GroupingMode.WORLD_DATE, "screenshots"));
        assertEquals("screenshots/My_World/the_nether/2026-01-30/",
                ConfigScreenLogic.buildExampleFolder(GroupingMode.WORLD_DIMENSION_DATE, "screenshots"));
        assertEquals("screenshots/My_World/2026-01-30/the_nether/",
                ConfigScreenLogic.buildExampleFolder(GroupingMode.WORLD_DATE_DIMENSION, "screenshots"));
        assertEquals("screenshots/",
                ConfigScreenLogic.buildExampleFolder(GroupingMode.NONE, "screenshots"));

        for (GroupingMode mode : GroupingMode.values()) {
            String desc = ConfigScreenLogic.getGroupingModeDescription(mode);
            assertNotNull(desc);
            assertFalse(desc.isBlank());
        }
        assertEquals(ConfigScreenLogic.getGroupingModeDescription(GroupingMode.WORLD),
                ConfigScreenLogic.getGroupingModeDescription(null));
    }

    @Test
    void buildExamplePathNormalizesBasePathAndNullMode() {
        assertEquals("screenshots/My_World/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.WORLD, null));
        assertEquals("screenshots/My_World/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.WORLD, "   "));
        assertEquals("/custom/dir/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(GroupingMode.NONE, "/custom/dir///"));
        assertEquals("screenshots/My_World/2026-01-30_14.22.05.png",
                ConfigScreenLogic.buildExamplePath(null, "screenshots"));
    }

    // --- buildGroupingGuideText ---

    @Test
    void buildGroupingGuideTextIncludesAllGroupingModesAndExamples() {
        String guide = ConfigScreenLogic.buildGroupingGuideText();

        assertNotNull(guide);
        assertFalse(guide.isBlank());
        for (GroupingMode mode : GroupingMode.values()) {
            assertTrue(guide.contains(mode.name()), "Guide missing mode: " + mode.name());
            assertTrue(guide.contains(ConfigScreenLogic.buildExamplePath(mode, "screenshots")),
                    "Guide missing example path for mode: " + mode.name());
        }
    }

    // --- applySaveState ---

    @Test
    void applySaveStateUpdatesGroupingModeAndMetadataWhenCustomPathDisabled() {
        ModConfig config = new ModConfig();
        String defaultPath = tempDir.toAbsolutePath().toString();

        ConfigScreenLogic.SaveOutcome outcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.WORLD_DATE, true, false, "", defaultPath);

        assertFalse(outcome.needsWarningDialog());
        assertFalse(outcome.customPathInvalid());
        assertNull(outcome.validationError());
        assertEquals(GroupingMode.WORLD_DATE, config.groupingMode);
        assertTrue(config.embedMetadata);
        assertFalse(config.customSavePathEnabled);
        assertTrue(config.advancedSettings);
    }

    @Test
    void applySaveStateRequiresWarningDialogWhenEnablingUnacknowledgedWithValidPath() {
        ModConfig config = new ModConfig();
        config.customPathConfig.hasAcknowledgedWarning = false;
        String validDir = tempDir.toAbsolutePath().toString();

        ConfigScreenLogic.SaveOutcome outcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.WORLD_DIMENSION, false, true, validDir, validDir);

        assertTrue(outcome.needsWarningDialog());
        assertFalse(outcome.customPathInvalid());
        assertNull(outcome.validationError());
        assertEquals(GroupingMode.WORLD_DIMENSION, config.groupingMode);
        assertEquals(validDir, config.customPathConfig.customPath);
        assertFalse(config.customSavePathEnabled, "Must stay disabled until warning is acknowledged");
        assertFalse(config.advancedSettings);
    }

    @Test
    void applySaveStateEnablesCustomPathDirectlyWhenWarningAlreadyAcknowledged() {
        ModConfig config = new ModConfig();
        config.customPathConfig.hasAcknowledgedWarning = true;
        String validDir = tempDir.toAbsolutePath().toString();

        ConfigScreenLogic.SaveOutcome outcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.DATE, false, true, validDir, validDir);

        assertFalse(outcome.needsWarningDialog());
        assertFalse(outcome.customPathInvalid());
        assertNull(outcome.validationError());
        assertEquals(validDir, config.customPathConfig.customPath);
        assertTrue(config.customSavePathEnabled);
        assertTrue(config.advancedSettings);
    }

    @Test
    void applySaveStatePopulatesDefaultScreenshotsPathWhenEmptyAndEnabling() {
        ModConfig config = new ModConfig();
        config.customPathConfig.hasAcknowledgedWarning = true;
        String defaultDir = tempDir.toAbsolutePath().toString();

        ConfigScreenLogic.SaveOutcome outcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.WORLD, false, true, "   ", defaultDir);

        assertFalse(outcome.needsWarningDialog());
        assertFalse(outcome.customPathInvalid());
        assertEquals(defaultDir, config.customPathConfig.customPath);
        assertTrue(config.customSavePathEnabled);
    }

    @Test
    void applySaveStateRejectsInvalidCustomPathWhenEnabling() {
        ModConfig config = new ModConfig();
        config.customPathConfig.hasAcknowledgedWarning = true;
        config.customPathConfig.customPath = "/original/valid/path";
        String nonExistentPath = tempDir.resolve("does_not_exist").toAbsolutePath().toString();

        ConfigScreenLogic.SaveOutcome outcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.NONE, false, true, nonExistentPath, tempDir.toAbsolutePath().toString());

        assertFalse(outcome.needsWarningDialog());
        assertTrue(outcome.customPathInvalid());
        assertNotNull(outcome.validationError());
        assertFalse(outcome.validationError().isBlank());
        assertFalse(config.customSavePathEnabled);
        assertEquals("/original/valid/path", config.customPathConfig.customPath,
                "Invalid path must not overwrite stored customPath");
        assertFalse(config.advancedSettings);
    }

    @Test
    void applySaveStateRejectsRelativePathAndFilePathWhenEnabling() throws IOException {
        ModConfig config = new ModConfig();
        config.customPathConfig.hasAcknowledgedWarning = true;

        ConfigScreenLogic.SaveOutcome relativeOutcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.WORLD, false, true, "relative/screenshots", tempDir.toAbsolutePath().toString());
        assertTrue(relativeOutcome.customPathInvalid());
        assertFalse(config.customSavePathEnabled);

        Path file = Files.createFile(tempDir.resolve("not_a_dir.txt"));
        ConfigScreenLogic.SaveOutcome fileOutcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.WORLD, false, true, file.toAbsolutePath().toString(),
                tempDir.toAbsolutePath().toString());
        assertTrue(fileOutcome.customPathInvalid());
        assertFalse(config.customSavePathEnabled);
    }

    @Test
    void applySaveStateUpdatesValidChangedPathEvenWhenDisabledAndIgnoresInvalidChangedPath() throws IOException {
        ModConfig config = new ModConfig();
        Path validSubDir = Files.createDirectory(tempDir.resolve("custom_shots"));
        String defaultDir = tempDir.toAbsolutePath().toString();

        ConfigScreenLogic.SaveOutcome validOutcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.WORLD, false, false, validSubDir.toAbsolutePath().toString(), defaultDir);
        assertFalse(validOutcome.customPathInvalid());
        assertFalse(validOutcome.needsWarningDialog());
        assertEquals(validSubDir.toAbsolutePath().toString(), config.customPathConfig.customPath);

        ConfigScreenLogic.SaveOutcome invalidOutcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.WORLD, false, false, "relative/invalid/path", defaultDir);
        assertFalse(invalidOutcome.customPathInvalid());
        assertEquals(validSubDir.toAbsolutePath().toString(), config.customPathConfig.customPath,
                "Invalid path when disabled must not overwrite previous valid path");
    }

    @Test
    void applySaveStateInitializesNullCustomPathConfigAndThrowsOnNullModConfig() {
        assertThrows(IllegalArgumentException.class, () -> ConfigScreenLogic.applySaveState(
                null, GroupingMode.WORLD, false, false, "", ""));

        ModConfig config = new ModConfig();
        config.customPathConfig = null;
        String validDir = tempDir.toAbsolutePath().toString();

        ConfigScreenLogic.SaveOutcome outcome = ConfigScreenLogic.applySaveState(
                config, GroupingMode.WORLD, false, true, validDir, validDir);

        assertNotNull(config.customPathConfig);
        assertTrue(outcome.needsWarningDialog());
        assertEquals(validDir, config.customPathConfig.customPath);
    }
}
