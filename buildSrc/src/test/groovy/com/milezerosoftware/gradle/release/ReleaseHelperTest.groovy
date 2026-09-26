package com.milezerosoftware.gradle.release

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class ReleaseHelperTest {

    @Test
    void testDetermineNextVersionMinorForFeatures() {
        def commits = [
            "feat: add Minecraft 26.2 support",
            "fix: correct toast manager handling"
        ]
        assertEquals("2.1.0", ReleaseHelper.determineNextVersion("2.0.0", commits))
    }

    @Test
    void testDetermineNextVersionPatchForFixes() {
        def commits = [
            "fix: null pointer when loading empty texture cache",
            "docs: update readme"
        ]
        assertEquals("2.0.1", ReleaseHelper.determineNextVersion("2.0.0", commits))
    }

    @Test
    void testDetermineNextVersionMajorForBreakingChange() {
        def commits = [
            "feat!: redesign config file format",
            "fix: handle migration"
        ]
        assertEquals("3.0.0", ReleaseHelper.determineNextVersion("2.0.0", commits))
    }

    @Test
    void testGenerateChangelogSection() {
        def commits = [
            "feat: add Minecraft 26.2 support",
            "fix: handle null level in world utils",
            "refactor: clean up screen utils",
            "chore: update gradle wrapper"
        ]
        String changelog = ReleaseHelper.generateChangelogSection("2.1.0", "2026-09-20", commits)
        assertTrue(changelog.contains("## [2.1.0] - 2026-09-20"))
        assertTrue(changelog.contains("### Added"))
        assertTrue(changelog.contains("Add Minecraft 26.2 support"))
        assertTrue(changelog.contains("### Fixed"))
        assertTrue(changelog.contains("Handle null level in world utils"))
        assertTrue(changelog.contains("### Changed"))
        assertTrue(changelog.contains("Clean up screen utils"))
        assertTrue(changelog.contains("### Internal"))
        assertTrue(changelog.contains("Update gradle wrapper"))
    }

    @Test
    void testExtractSection() {
        String fullChangelog = """# Changelog

## [2.1.0] - 2026-09-20

### Added
- Feature 1

### Fixed
- Fix 1

---

## [2.0.0] - 2026-05-18

### Added
- Feature 0
"""
        String section = ReleaseHelper.extractSection(fullChangelog, "2.1.0")
        assertTrue(section.contains("### Added"))
        assertTrue(section.contains("Feature 1"))
        assertFalse(section.contains("Feature 0"))
    }

    @Test
    void testGetPlayerFacingNotes() {
        String fullChangelog = """## [2.1.0] - 2026-09-20

### Added
- Minecraft 26.2 Support

### Changed
- Improved gallery performance

### Internal & Development
- ScreenUtils abstraction
- Release automation tooling
"""
        String playerNotes = ReleaseHelper.getPlayerFacingNotes(fullChangelog)
        assertTrue(playerNotes.contains("### Added"))
        assertTrue(playerNotes.contains("Minecraft 26.2 Support"))
        assertTrue(playerNotes.contains("### Changed"))
        assertTrue(playerNotes.contains("Improved gallery performance"))
        assertFalse(playerNotes.contains("### Internal & Development"))
        assertFalse(playerNotes.contains("ScreenUtils abstraction"))
    }

    @Test
    void testParseDotEnv() {
        String envContent = """
# This is a comment
MODRINTH_TOKEN=mrp_test12345
GITHUB_TOKEN="ghp_quotedtoken"
SINGLE_QUOTED='single_value'
EMPTY_LINE=
# Another comment
SPACED_KEY = spaced_value
"""
        Map<String, String> parsed = ReleaseHelper.parseDotEnv(envContent)
        assertEquals("mrp_test12345", parsed["MODRINTH_TOKEN"])
        assertEquals("ghp_quotedtoken", parsed["GITHUB_TOKEN"])
        assertEquals("single_value", parsed["SINGLE_QUOTED"])
        assertEquals("", parsed["EMPTY_LINE"])
        assertEquals("spaced_value", parsed["SPACED_KEY"])
        assertNull(parsed["NON_EXISTENT"])
    }

    @Test
    void testGetEnvFromFile() {
        File temp = File.createTempFile("test", ".env")
        try {
            temp.text = "MODRINTH_TOKEN=mrp_file_token\n"
            assertEquals("mrp_file_token", ReleaseHelper.getEnvFromFile(temp, "MODRINTH_TOKEN"))
            assertNull(ReleaseHelper.getEnvFromFile(temp, "UNKNOWN_KEY"))
            assertNull(ReleaseHelper.getEnvFromFile(new File("nonexistent.env"), "MODRINTH_TOKEN"))
            assertNull(ReleaseHelper.getEnvFromFile(null, "MODRINTH_TOKEN"))
        } finally {
            temp.delete()
        }
    }
}
