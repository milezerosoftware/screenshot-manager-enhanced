package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import java.io.File;

/**
 * Utility class for path validation.
 */
public class FilePickerHelper {

    /**
     * Validates that a path exists, is a directory, and is writable.
     *
     * @param path The path to validate.
     * @return A validation result with error message if invalid.
     */
    public static ValidationResult validateDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return new ValidationResult(false, "Path cannot be empty");
        }

        File dir = new File(path);

        if (!dir.isAbsolute()) {
            return new ValidationResult(false, "Path must be absolute");
        }

        if (!dir.exists()) {
            return new ValidationResult(false, "Directory does not exist: " + path);
        }

        if (!dir.isDirectory()) {
            return new ValidationResult(false, "Path is not a directory: " + path);
        }

        if (!dir.canWrite()) {
            return new ValidationResult(false, "Directory is not writable: " + path);
        }

        return new ValidationResult(true, null);
    }

    /**
     * Result of directory validation.
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;

        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }
}
