package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility class for opening native OS file/directory picker dialogs.
 * Uses LWJGL3's TinyFileDialogs for cross-platform support.
 */
public class FilePickerHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("screenshot-manager-enhanced");

    /**
     * Opens a native directory picker dialog.
     *
     * @param title      The title of the dialog window.
     * @param initialDir The initial directory to display (can be null).
     * @return An Optional containing the selected directory path, or empty if
     *         cancelled.
     */
    public static Optional<Path> openDirectoryPicker(String title, Path initialDir) {
        try {
            String initialPath = initialDir != null ? initialDir.toAbsolutePath().toString() : null;
            String result = TinyFileDialogs.tinyfd_selectFolderDialog(title, initialPath);

            if (result != null && !result.isEmpty()) {
                File selectedDir = new File(result);
                if (selectedDir.isDirectory()) {
                    return Optional.of(selectedDir.toPath());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to open directory picker: {}", e.getMessage());
        }
        return Optional.empty();
    }

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
