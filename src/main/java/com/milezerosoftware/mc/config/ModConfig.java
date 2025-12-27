package com.milezerosoftware.mc.config;

public class ModConfig {
    // Global Settings
    public boolean enableMetadata = true;
    public GroupingMode groupingMode = GroupingMode.DATE;

    // Default storage folder name (fallback)
    public String customPath = "screenshots";

    // Per-World Rules: Key = WorldName/IP, Value = Configuration
    public java.util.Map<String, WorldConfig> worldRules = new java.util.HashMap<>();

    public ModConfig() {
    }
}