package com.milezerosoftware.mc.config;

/**
 * Defines the strategy for grouping screenshots in the file system.
 */
public enum GroupingMode {
    /**
     * Group screenshots by date (e.g., yyyy-MM-dd).
     */
    DATE,

    /**
     * Group screenshots by project/world name.
     */
    PROJECT,

    /**
     * Do not group screenshots; store them in the root of the configured path.
     */
    NONE
}
