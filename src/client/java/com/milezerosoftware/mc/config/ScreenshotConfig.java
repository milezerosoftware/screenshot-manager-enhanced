package com.milezerosoftware.mc.config;

public class ScreenshotConfig {
    public static final ScreenshotConfig INSTANCE = new ScreenshotConfig();

    // Default to the standard "screenshots" folder name
    public String folderName = "screenshots";
    public boolean useSubfolders = false;
}