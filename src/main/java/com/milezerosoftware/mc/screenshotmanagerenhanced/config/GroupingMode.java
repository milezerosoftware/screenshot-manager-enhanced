package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

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
    /**
     * Group screenshots by world name.
     */
    WORLD,

    /**
     * Group screenshots by world name, then by dimension.
     */
    WORLD_DIMENSION,

    /**
     * Group screenshots by world name, then by date.
     */
    WORLD_DATE,

    /**
     * Group screenshots by world name, then by dimension, then by date.
     */
    WORLD_DIMENSION_DATE,

    /**
     * Group screenshots by world name, then by date, then by dimension.
     */
    WORLD_DATE_DIMENSION,

    /**
     * Group screenshots by project/world name.
     * 
     * @deprecated Use {@link #WORLD} instead.
     */
    @Deprecated
    PROJECT,

    /**
     * Do not group screenshots; store them in the root of the configured path.
     */
    NONE
}
