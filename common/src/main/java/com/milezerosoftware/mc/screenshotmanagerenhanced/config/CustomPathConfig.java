package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

/**
 * Configuration container for custom screenshot save path settings.
 * The enabled flag is external (in ModConfig) for direct config file editing.
 */
public class CustomPathConfig {
    /**
     * Whether the user has acknowledged the warning about manual world name
     * management.
     */
    public boolean hasAcknowledgedWarning = false;

    /**
     * The custom screenshot directory path (absolute path).
     */
    public String customPath = "";

    public CustomPathConfig() {
    }

    public CustomPathConfig(boolean hasAcknowledgedWarning, String customPath) {
        this.hasAcknowledgedWarning = hasAcknowledgedWarning;
        this.customPath = customPath;
    }
}
