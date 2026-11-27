package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Pure-Java helper for constructing safe OS clipboard command lines.
 */
public final class ClipboardCommandHelper {

    private ClipboardCommandHelper() {
    }

    /**
     * Builds an {@code osascript} command line that passes {@code imagePath} safely via {@code argv}
     * rather than interpolating it into AppleScript string literals.
     */
    public static List<String> buildMacOsascriptCommand(Path imagePath) {
        Objects.requireNonNull(imagePath, "imagePath");
        return List.of(
                "osascript",
                "-e", "on run argv",
                "-e", "set the clipboard to (read (POSIX file (item 1 of argv)) as TIFF picture)",
                "-e", "end run",
                "--",
                imagePath.toAbsolutePath().toString()
        );
    }
}
