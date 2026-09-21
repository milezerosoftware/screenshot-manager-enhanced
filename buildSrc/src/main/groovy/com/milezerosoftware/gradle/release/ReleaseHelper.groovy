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

            if (msg.startsWith("feat:") || msg.startsWith("feat(")) {
                added.add(cleanCommitMsg(msg))
            } else if (msg.startsWith("fix:") || msg.startsWith("fix(")) {
                fixed.add(cleanCommitMsg(msg))
            } else if (msg.startsWith("refactor:") || msg.startsWith("refactor(") || msg.startsWith("perf:") || msg.startsWith("perf(")) {
                changed.add(cleanCommitMsg(msg))
            } else {
                internal.add(cleanCommitMsg(msg))
            }
        }

        StringBuilder sb = new StringBuilder()
        sb.append("## [${version}] - ${dateStr}\n\n")

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

        if (!internal.isEmpty()) {
            sb.append("### Internal\n\n")
            internal.each { sb.append("- ${it}\n") }
            sb.append("\n")
        }

        sb.append("---\n")
        return sb.toString().trim()
    }

    private static String cleanCommitMsg(String msg) {
        // Strip common prefixes like 'feat: ' or 'fix: '
        return msg.replaceFirst(/^(feat|fix|refactor|perf|chore|docs|test|style)(\([^\)]+\))?:\s*/, "")
                  .capitalize()
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
