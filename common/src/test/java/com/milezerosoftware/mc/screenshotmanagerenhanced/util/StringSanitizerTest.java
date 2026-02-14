package com.milezerosoftware.mc.screenshotmanagerenhanced.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StringSanitizer}.
 * <p>
 * Validates the filesystem sanitization logic used to create safe directory
 * names
 * from world names, server names, and other user-facing strings.
 */
public class StringSanitizerTest {

    @Test
    void testAlphanumericUnchanged() {
        assertEquals("HelloWorld123", StringSanitizer.sanitizeForFilesystem("HelloWorld123"));
    }

    @Test
    void testSpacesReplacedWithUnderscore() {
        assertEquals("My_World_Save", StringSanitizer.sanitizeForFilesystem("My World Save"));
    }

    @Test
    void testDotsPreserved() {
        assertEquals("server.example.com", StringSanitizer.sanitizeForFilesystem("server.example.com"));
    }

    @Test
    void testHyphensPreserved() {
        assertEquals("my-cool-world", StringSanitizer.sanitizeForFilesystem("my-cool-world"));
    }

    @Test
    void testParenthesesPreserved() {
        assertEquals("World(1)", StringSanitizer.sanitizeForFilesystem("World(1)"));
    }

    @Test
    void testWindowsForbiddenCharacters() {
        // Windows forbids: < > : " / \ | ? *
        String input = "World<>:\"/\\|?*Name";
        String result = StringSanitizer.sanitizeForFilesystem(input);
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertFalse(result.contains(":"));
        assertFalse(result.contains("\""));
        assertFalse(result.contains("/"));
        assertFalse(result.contains("\\"));
        assertFalse(result.contains("|"));
        assertFalse(result.contains("?"));
        assertFalse(result.contains("*"));
        assertEquals("World_________Name", result);
    }

    @Test
    void testUnicodeReplacedWithUnderscore() {
        assertEquals("___", StringSanitizer.sanitizeForFilesystem("日本語"));
    }

    @Test
    void testEmojiReplaced() {
        assertEquals("World__", StringSanitizer.sanitizeForFilesystem("World🌍🏠"));
    }

    @Test
    void testEmptyString() {
        assertEquals("", StringSanitizer.sanitizeForFilesystem(""));
    }

    @Test
    void testMinecraftDimension() {
        // Typical Minecraft dimension strings
        assertEquals("minecraft_overworld", StringSanitizer.sanitizeForFilesystem("minecraft:overworld"));
    }

    @Test
    void testServerAddress() {
        // IP addresses and ports
        assertEquals("192.168.1.1_25565", StringSanitizer.sanitizeForFilesystem("192.168.1.1:25565"));
    }

    @ParameterizedTest
    @CsvSource({
            "'a',       'a'",
            "'A',       'A'",
            "'0',       '0'",
            "'.',       '.'",
            "'-',       '-'",
            "'(',       '('",
            "')',       ')'",
            "' ',       '_'",
            "'!',       '_'",
            "'@',       '_'",
            "'#',       '_'",
            "'$',       '_'",
            "'%',       '_'",
            "'^',       '_'",
            "'&',       '_'",
    })
    void testSingleCharacters(String input, String expected) {
        assertEquals(expected, StringSanitizer.sanitizeForFilesystem(input));
    }
}
