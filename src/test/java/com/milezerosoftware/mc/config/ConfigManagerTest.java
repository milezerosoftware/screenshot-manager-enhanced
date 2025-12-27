package com.milezerosoftware.mc.config;

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
        config.customPath = "custom/screenshots";
        config.groupingMode = GroupingMode.PROJECT;
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
        assertEquals("custom/screenshots", loadedConfig.customPath);
        assertEquals(GroupingMode.PROJECT, loadedConfig.groupingMode);
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
        assertEquals("screenshots", config.customPath);
        assertEquals(GroupingMode.DATE, config.groupingMode);
        assertTrue(config.enableMetadata);
        assertTrue(Files.exists(configFile), "Config file should be created if missing");
    }

    @Test
    void testMalformedJson() throws IOException {
        Path configFile = tempDir.resolve("malformed_config.json");
        Files.writeString(configFile, "{ \"customPath\": \"broken\", \"incompl...");

        ConfigManager.load(configFile);
        ModConfig config = ConfigManager.getInstance();

        assertNotNull(config, "Config should not be null after malformed load");
        assertEquals("screenshots", config.customPath, "Should fallback to default on error");
    }
}
