package com.milezerosoftware.mc.config;

import java.util.HashMap;
import java.util.Map;

public class ModConfig {
    // Global Settings
    public boolean enableMetadata = true;
    public GroupingMode groupingMode = GroupingMode.WORLD; // Default to WORLD

    // Per-World Rules: Key = WorldName/IP, Value = Configuration
    public Map<String, WorldConfig> worldRules = new HashMap<>();

    public ModConfig() {
    }
}