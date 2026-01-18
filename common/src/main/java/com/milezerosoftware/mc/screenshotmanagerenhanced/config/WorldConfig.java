package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

/**
 * Configuration settings specific to a single world or server.
 */
public class WorldConfig {
    public String customPath = "";
    public boolean active = true;

    public WorldConfig() {
    }

    public WorldConfig(String customPath, boolean active) {
        this.customPath = customPath;
        this.active = active;
    }
}
