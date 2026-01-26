package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import java.io.File;

/**
 * Data Transfer Object for passing screenshot file and metadata between
 * threads.
 * 
 * <p>
 * This class holds both the target file path and the collected metadata,
 * allowing safe transfer from the Render thread (where metadata is collected)
 * to background threads (where metadata is written).
 * </p>
 * 
 * <p>
 * <strong>Note:</strong> This class must remain outside the mixin package
 * to avoid class loading issues with Mixin's restricted package handling.
 * </p>
 * 
 * @see MetadataHandler.ScreenshotMetadata
 */
public class MetadataTask {

    /** The target screenshot file path. */
    public final File file;

    /** The collected metadata to embed. */
    public final MetadataHandler.ScreenshotMetadata metadata;

    /**
     * Creates a new MetadataTask.
     *
     * @param file     The target screenshot file
     * @param metadata The metadata to embed
     */
    public MetadataTask(File file, MetadataHandler.ScreenshotMetadata metadata) {
        this.file = file;
        this.metadata = metadata;
    }
}
