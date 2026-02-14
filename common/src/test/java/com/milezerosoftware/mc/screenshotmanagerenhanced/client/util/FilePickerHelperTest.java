package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FilePickerHelper}.
 * <p>
 * Validates directory path validation logic used by the mod config screen
 * to ensure only valid, writable directories are accepted as custom save paths.
 */
public class FilePickerHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void testNullPath() {
        FilePickerHelper.ValidationResult result = FilePickerHelper.validateDirectory(null);
        assertFalse(result.isValid);
        assertEquals("Path cannot be empty", result.errorMessage);
    }

    @Test
    void testEmptyPath() {
        FilePickerHelper.ValidationResult result = FilePickerHelper.validateDirectory("");
        assertFalse(result.isValid);
        assertEquals("Path cannot be empty", result.errorMessage);
    }

    @Test
    void testRelativePath() {
        FilePickerHelper.ValidationResult result = FilePickerHelper.validateDirectory("relative/path");
        assertFalse(result.isValid);
        assertEquals("Path must be absolute", result.errorMessage);
    }

    @Test
    void testNonExistentPath() {
        String fakePath = tempDir.resolve("does_not_exist").toAbsolutePath().toString();
        FilePickerHelper.ValidationResult result = FilePickerHelper.validateDirectory(fakePath);
        assertFalse(result.isValid);
        assertTrue(result.errorMessage.startsWith("Directory does not exist:"));
    }

    @Test
    void testPathIsFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("not_a_directory.txt"));
        FilePickerHelper.ValidationResult result = FilePickerHelper.validateDirectory(
                file.toAbsolutePath().toString());
        assertFalse(result.isValid);
        assertTrue(result.errorMessage.startsWith("Path is not a directory:"));
    }

    @Test
    void testValidDirectory() {
        FilePickerHelper.ValidationResult result = FilePickerHelper.validateDirectory(
                tempDir.toAbsolutePath().toString());
        assertTrue(result.isValid);
        assertNull(result.errorMessage);
    }

    @Test
    void testValidSubdirectory() throws IOException {
        Path subDir = Files.createDirectory(tempDir.resolve("screenshots"));
        FilePickerHelper.ValidationResult result = FilePickerHelper.validateDirectory(
                subDir.toAbsolutePath().toString());
        assertTrue(result.isValid);
        assertNull(result.errorMessage);
    }

    @Test
    void testNonWritableDirectory() throws IOException {
        Path readOnly = Files.createDirectory(tempDir.resolve("readonly"));
        File readOnlyFile = readOnly.toFile();
        boolean wasSet = readOnlyFile.setWritable(false);

        // Skip if OS doesn't support revoking write permissions (e.g., some CI envs)
        if (!wasSet || readOnlyFile.canWrite()) {
            readOnlyFile.setWritable(true); // Cleanup
            return; // Cannot test non-writable on this platform
        }

        try {
            FilePickerHelper.ValidationResult result = FilePickerHelper.validateDirectory(
                    readOnly.toAbsolutePath().toString());
            assertFalse(result.isValid);
            assertTrue(result.errorMessage.startsWith("Directory is not writable:"));
        } finally {
            readOnlyFile.setWritable(true); // Cleanup
        }
    }
}
