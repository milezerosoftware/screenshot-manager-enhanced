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
}
