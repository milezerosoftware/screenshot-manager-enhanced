package com.milezerosoftware.mc.screenshotmanagerenhanced.util;

import org.jetbrains.annotations.NotNull;

/**
 * Shared string sanitization utilities for filesystem-safe names.
 * <p>
 * This class lives in the {@code main} source set so it can be used by both
 * client-side code (e.g., {@code WorldUtils}) and tested directly.
 */
public final class StringSanitizer {

    private StringSanitizer() {
        // Utility class
    }

    /**
     * Sanitizes a string for use as a filesystem directory name.
     * <p>
     * Replaces any character that isn't alphanumeric, a dot, a hyphen, or
     * parentheses with an underscore. This ensures compatibility with strict
     * file system naming rules (e.g., Windows forbids {@code < > : " / \ | ? *}).
     *
     * @param input The string to sanitize.
     * @return A sanitized version of the string suitable for directory names.
     */
    @NotNull
    public static String sanitizeForFilesystem(@NotNull String input) {
        return input.replaceAll("[^a-zA-Z0-9\\.\\-\\(\\)]", "_");
    }
}
