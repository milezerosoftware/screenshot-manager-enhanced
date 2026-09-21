package com.milezerosoftware.gradle.release

import java.time.LocalDate

class ReleaseHelper {

    static String determineNextVersion(String currentVersion, List<String> commitMessages) {
        def parts = currentVersion.split('\\.')
        int major = parts.length > 0 ? parts[0].toInteger() : 0
        int minor = parts.length > 1 ? parts[1].toInteger() : 0
        int patch = parts.length > 2 ? parts[2].toInteger() : 0

        boolean isMajor = commitMessages.any { it.contains("BREAKING CHANGE") || it.contains("!:") }
        boolean isMinor = commitMessages.any { it.startsWith("feat:") || it.startsWith("feat(") }

        if (isMajor) {
            return "${major + 1}.0.0"
        } else if (isMinor) {
            return "${major}.${minor + 1}.0"
        } else {
            return "${major}.${minor}.${patch + 1}"
        }
    }

    /**
     * Generates a structured changelog section following the user-first style of release 2.0.0.
     * Player/user-facing sections (Added, Changed, Fixed) come first, followed by Internal & Development.
     */
    static String generateChangelogSection(String version, String dateStr, List<String> commits) {
        List<String> added = []
        List<String> changed = []
        List<String> fixed = []
        List<String> internal = []

        commits.each { rawMsg ->
            def msg = rawMsg.trim()
            if (msg.isEmpty() || msg.startsWith("Merge ") || msg.startsWith("chore(release):") || msg.startsWith("chore: release")) {
                return
            }

            // Distinguish user-facing features vs internal changes
            if (msg.startsWith("feat:") || msg.startsWith("feat(")) {
                // If it's a version port or game feature, it's user-facing Added
                added.add(formatUserFacing(msg))
            } else if (msg.startsWith("fix:") || msg.startsWith("fix(")) {
                fixed.add(formatUserFacing(msg))
            } else if (msg.startsWith("refactor:") || msg.startsWith("refactor(") || msg.startsWith("perf:") || msg.startsWith("perf(")) {
                changed.add(formatUserFacing(msg))
            } else {
                internal.add(formatInternal(msg))
            }
        }

        StringBuilder sb = new StringBuilder()
        sb.append("## [${version}] - ${dateStr}\n\n")

        // 1. Gamer / Mod-User Focused Sections First
        if (!added.isEmpty()) {
            sb.append("### Added\n\n")
            added.each { sb.append("- ${it}\n") }
            sb.append("\n")
        }

        if (!changed.isEmpty()) {
            sb.append("### Changed\n\n")
            changed.each { sb.append("- ${it}\n") }
            sb.append("\n")
        }

        if (!fixed.isEmpty()) {
            sb.append("### Fixed\n\n")
            fixed.each { sb.append("- ${it}\n") }
            sb.append("\n")
        }

        // 2. Technical & Architecture Details Second
        if (!internal.isEmpty()) {
            sb.append("### Internal & Development\n\n")
            internal.each { sb.append("- ${it}\n") }
            sb.append("\n")
        }

        sb.append("---\n")
        return sb.toString().trim()
    }

    private static String formatUserFacing(String msg) {
        String clean = msg.replaceFirst(/^(feat|fix|refactor|perf)(\([^\)]+\))?:\s*/, "").trim()
        // Bold the leading topic if appropriate (e.g. "Minecraft 26.2 Support: ...")
        if (clean.toLowerCase().contains("minecraft") || clean.toLowerCase().contains("support")) {
            def parts = clean.split(/(\s*-\s*|\s*:\s*)/, 2)
            if (parts.length == 2) {
                return "**${parts[0].capitalize()}**: ${parts[1].capitalize()}"
            }
        }
        return clean.capitalize()
    }

    private static String formatInternal(String msg) {
        return msg.replaceFirst(/^(chore|docs|test|style|ci|build)(\([^\)]+\))?:\s*/, "")
                  .capitalize()
    }

    /**
     * Extracts only player-facing sections (Added, Changed, Fixed) for Modrinth,
     * omitting Internal & Development details.
     */
    static String getPlayerFacingNotes(String fullNotes) {
        StringBuilder sb = new StringBuilder()
        boolean inInternal = false

        fullNotes.eachLine { line ->
            if (line.startsWith("### Internal")) {
                inInternal = true
                return
            }
            if (inInternal) {
                // If another h3 appears after internal, end internal skipping
                if (line.startsWith("### ")) {
                    inInternal = false
                } else {
                    return
                }
            }
            sb.append(line).append("\n")
        }

        return sb.toString().trim()
    }

    static String extractSection(String changelogContent, String version) {
        def lines = changelogContent.readLines()
        boolean inSection = false
        StringBuilder sb = new StringBuilder()

        for (String line : lines) {
            if (line.startsWith("## [${version}]")) {
                inSection = true
                continue
            }
            if (inSection) {
                if (line.startsWith("## [") || line.startsWith("---")) {
                    break
                }
                sb.append(line).append("\n")
            }
        }
        return sb.toString().trim()
    }
}
