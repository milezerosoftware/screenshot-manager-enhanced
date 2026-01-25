package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import java.io.File;

/**
 * DTO to pass screenshot file and metadata between injection points.
 * Ensures thread-safe data transfer from Render thread to IO thread.
 * 
 * This class is outside the mixin package to avoid class loading issues.
 */
public class MetadataTask {
    public final File file;
    public final MetadataHandler.ScreenshotMetadata metadata;

    public MetadataTask(File file, MetadataHandler.ScreenshotMetadata metadata) {
        this.file = file;
        this.metadata = metadata;
    }
}
