package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

import java.util.HashMap;
import java.util.Map;

public class ModConfig {
    // Global Settings
    public boolean embedMetadata = false; // Default to disabled
    public boolean displayRelativePath = true; // Default to enabled
    public GroupingMode groupingMode = GroupingMode.WORLD; // Default to WORLD

    // Per-World Rules: Key = WorldName/IP, Value = Configuration
    public Map<String, WorldConfig> worldRules = new HashMap<>();

    public ModConfig() {
    }
}