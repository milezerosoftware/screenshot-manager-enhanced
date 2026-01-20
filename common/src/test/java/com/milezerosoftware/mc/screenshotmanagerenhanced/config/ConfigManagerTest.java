package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveAndLoad() throws IOException {
        Path configFile = tempDir.resolve("test_config.json");

        // 1. Setup initial state
        ConfigManager.load(configFile);
        ModConfig config = ConfigManager.getInstance();
        config.groupingMode = GroupingMode.WORLD;
        config.worldRules.put("TestWorld", new WorldConfig("worlds/test", false));

        // 2. Save
        ConfigManager.save(configFile);

        // 3. Clear instance to force reload (Simulate restart)
        // Since ConfigManager singleton is simple, we might need a way to reset it or
        // just load again
        // Ideally we'd reset 'instance', but it's private.
        // However, calling load(path) overwrites 'instance', so that works.
        ConfigManager.load(configFile);
        ModConfig loadedConfig = ConfigManager.getInstance();

        // 4. Verify
        assertEquals(GroupingMode.WORLD, loadedConfig.groupingMode);
        assertTrue(loadedConfig.worldRules.containsKey("TestWorld"));
        assertEquals("worlds/test", loadedConfig.worldRules.get("TestWorld").customPath);
        assertFalse(loadedConfig.worldRules.get("TestWorld").active);
    }

    @Test
    void testDefaults() {
        Path configFile = tempDir.resolve("missing_config.json");
        ConfigManager.load(configFile);
        ModConfig config = ConfigManager.getInstance();

        assertNotNull(config);
        assertEquals(GroupingMode.WORLD, config.groupingMode);
        assertTrue(config.enableMetadata);
        assertTrue(Files.exists(configFile), "Config file should be created if missing");
    }

    @Test
    void testMalformedJson() throws IOException {
        Path configFile = tempDir.resolve("malformed_config.json");
        Files.writeString(configFile, "{ \"groupingMode\": \"broken\", \"incompl...");

        ConfigManager.load(configFile);
        ModConfig config = ConfigManager.getInstance();

        assertNotNull(config, "Config should not be null after malformed load");

        // Check for backup file
        Path backupFile = tempDir.resolve("malformed_config.json.broken");
        assertTrue(Files.exists(backupFile), "Backup file should be created for malformed config");
    }

    @Test
    void testEmptyFile() throws IOException {
        Path configFile = tempDir.resolve("empty_config.json");
        Files.createFile(configFile); // Create 0-byte file

        ConfigManager.load(configFile);
        ModConfig config = ConfigManager.getInstance();

        assertNotNull(config, "Config should not be null after empty load");

        // Verify that the file was "repaired" (i.e., defaults saved to it)
        String content = Files.readString(configFile);
        assertFalse(content.isEmpty(), "Config file should be repopulated with defaults");
        // Verify recursion didn't happen (if strict timeout logic isn't in place,
        // implicit success is passing this line)
    }
}
