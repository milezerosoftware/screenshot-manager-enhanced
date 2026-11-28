# Changelog

All notable changes to Screenshot Manager Enhanced will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2026-11-27

### Added

- **Gallery Metadata Inspector (#65)**: Added a toggleable **Info** panel (`I` key or Info button) in the single-image viewer (`SingleImageScreen`) displaying embedded Minecraft XMP metadata (World, Dimension, Biome, Coordinates with one-click Copy, Difficulty, Days Played, World Age, MC Version, Player, Game Mode) and file details (Resolution, File Size, Modified timestamp) with async streaming PNG chunk reading and responsive side-by-side / overlay layouts
- **Gallery Keyboard Navigation (#63)**: Added full keyboard navigation across the gallery grid (`Arrow` keys, `PageUp`/`PageDown`, `Enter` to open) and single-image view (`Left`/`Right` arrows), with gold selection outlines, automatic scroll-into-view, and preserved selection/page state on return
- **Gallery Pagination**: Added paginated gallery browsing (`24`, `36`, or `48` images per page depending on `3`, `6`, or `8` column layout) with `< Prev` / `Next >` controls and total image counts
- **Unified YACL Configuration Screen (#61)**: Replaced the legacy Cloth Config screens with a unified **YetAnotherConfigLib (YACL)** `SME Settings` screen accessible via Mod Menu, featuring live Grouping Mode folder path previews, Metadata embedding toggle, and Custom Save Location validation
- **Modrinth Sandbox & Draft Support**: Added Modrinth staging sandbox (`MODRINTH_STAGING`), draft upload mode (`MODRINTH_DRAFT`), Base62 project ID resolution, and OpenAPI schema compliance to release publishing

### Changed

- **Configuration Library Migration (Breaking, #61)**: Replaced Cloth Config API with **YetAnotherConfigLib (YACL)** as a required dependency across Minecraft `1.21.11`, `26.1.2`, and `26.2`
- **Gallery Performance & Texture Caching**: Refactored `ScreenshotTextureManager` with a bounded LRU GPU texture cache, bounded worker pool, frame-budgeted uploads, generation-based request cancellation, aspect-ratio-preserving thumbnail sizing, and adjacent image prefetching
- **Async Scanning & Safe Clipboard I/O**: Added single-pass `GalleryDirectoryScanner` (`O(N)` attribute reads, newest-first sorting) and moved single-image file deletion and clipboard operations off the render thread with argument-safe command invocation
- **Version Baseline Enforcement**: Added fail-fast validation in `settings.gradle` for Minecraft targets below the `1.21.11` baseline

### Removed

- **Cloth Config Dependency**: Dropped Cloth Config API and legacy version-specific config screen implementations (`java-21`, `java-26`, `java-26-2`) in favor of shared YACL configuration
- **Obsolete MetadataTask**: Removed legacy `MetadataTask` class superseded by `MetadataHandler`

### Fixed

- **Minecraft 26.2 Screen Compatibility**: Routed active screen and toast/clipboard checks through version-specific `ScreenUtils` bridges
- **Metadata Embedding Reliability**: Hardened `MetadataHandler` file-stability polling and atomic temp-file replacement
- **Modrinth Release Uploads**: Fixed Base62 project ID resolution and CreatableVersion OpenAPI payload formatting in `publishRelease`

### Internal & Development

- Consolidated `ModMenuIntegration` into shared `common/src/client/java/` and removed duplicate version-specific config screens (`java-21`, `java-26`, `java-26-2`) and `MetadataTask`
- Added JUnit 5 unit tests covering gallery navigation, directory scanning, metadata parsing/formatting, config screen logic, LRU thumbnail cache, sizing, clipboard command building, metadata handling, and release helpers
- Added Gradle dependency caching and PR concurrency deduplication to `.github/workflows/build.yml`
- Updated `RELEASE.md` with `.env`, staging sandbox, and resumable release documentation

---

## [2.1.0] - 2026-09-26

### Added

- Enhance changelog synthesis to prioritize player-facing notes for Modrinth
- Add guided two-phase Gradle release automation and dry-run engine
- Add Minecraft 26.2 support and update build docs

### Internal & Development

- Remove obsolete release.yml and update RELEASE.md
- Migrate GEMINI.md to AGENTS.md and update for 2.0.0 architecture

---

## [2.0.0] - 2026-05-18

### Added

- **Minecraft 26.1.2 Support**: Fully functional client compilation and packaging under the modern Minecraft `26.1.2` build classpath using Java 25.
- **Java 25 Compilation Tooling**: Upgraded version configurations and matrix resolution workflows to seamlessly support newer target environments.

### Changed

- **Mojang Mappings Migration**: Transitioned the primary compilation mapping baseline from old Yarn mappings to standard official Mojang mappings for all active releases (`1.21.11` and `26.1.2`), ensuring high compatibility with vanilla APIs.
- **Reactive UI Modernization**: Rewrote visual hover effects in the screenshot gallery UI using **owo-lib's reactive mouse-event framework** (`mouseEnter`/`mouseLeave` subscriptions), completely abstracting target-specific rendering contexts from screen definitions.
- **Client Code Consolidation**: Unified complex GUI controllers (`GalleryScreen`, `SingleImageScreen`, `ScreenshotMetadataCollector`) back into a single shared source set in the `common` subproject, deleting over 800 lines of duplicate client-side classes.
- **Consistent Directory Semantics**: Renamed the historical `java-modern` directory to `java-21` to match properties definitions and reflect its targeted Minecraft 1.21 baseline.

### Removed

- **Legacy Client Classes**: Deleted the now-unused `java-8`, `java-17`, and legacy `fabric` client source sets (20+ files)
- **Obsolete Build Configurations**: Removed `java8.gradle.kts` and related legacy build logic
- **Build Matrix Redundancy**: Simplified the CI/CD matrix to use a single `versions.properties`-driven configuration

---

## [1.4.0] - 2026-02-14

- **In-Game Gallery** (#62):
  - Core gallery implementation with responsive grid view (`5d4ceb7`)
  - Single image review screen (`SingleImageScreen`)
  - Clipboard integration for copying screenshots (`1d298ca`)
  - Native OS Trash/Recycle Bin support for deletions
  - Navigation controls (Next/Previous)

### Changed

- **UI Polish**: Gallery UI overhaul with responsive layout and improved interactions (`00f790b`)

### Internal

- **Architecture**: Multi-version project restructuring for future loader support (`2d1da5b`)
- **Testing**: Expanded unit test coverage for gallery features (`c8d1e4b`)

---

## [1.3.0] - 2026-02-01

### Added

- **XMP Metadata Embedding** (#50): Screenshots can now include embedded metadata (World Name, Coordinates, Biome, Timestamp, Server IP) readable by tools like Lightroom and Photoshop
- **Custom Save Path** (#52, #53): Define exactly where screenshots are saved — use cloud storage, external drives, or any folder you choose
- **Advanced Features Section**: New collapsible settings area in Mod Menu for power-user options
- **Gallery Documentation** (#54): New `Gallery.md` showcasing the mod's UI and features
- **Automated Publishing** (#56): Releases now auto-publish to Modrinth and CurseForge via `mc-publish` GitHub Action
- **Release Runbook**: New `RELEASE.md` with step-by-step instructions and Quick Reference Card
- **Dynamic Version Matrix**: CI/CD workflows derive Minecraft versions from `versionProperties/` directory

### Changed

- Metadata embedding is disabled by default (opt-in via Mod Menu)
- Custom path requires acknowledgment of warning dialog before enabling
- Release workflow uses matrix strategy for parallel multi-version builds
- Sources JAR skipped in release builds for faster builds (#57)
- Updated README with simplified release process and Gallery link

### Internal

- Centralized version management — adding a new MC version only requires a new `.properties` file

---

## [1.2.0] - 2026-01-24

### Added

- **ModMenu Integration**: Full in-game configuration UI via Mod Menu
- **Grouping Mode UI**: Visual selector for all grouping modes with descriptions
- Marketing assets and media images for documentation

### Changed

- Release workflow now creates draft releases with auto-generated notes
- Brightened media images for better visibility

### Internal

- CI/CD improvements for release automation

---

## [1.0.0] - 2026-01-10

### Added

- **Automatic Organization**: Screenshots are instantly sorted into subfolders upon capture
- **Per-World Configuration**: Customize settings for single-player or specific server worlds
- **Flexible Grouping Modes**:
  - `WORLD` (Default): Groups screenshots by the world or server name
  - `DATE`: Groups screenshots by the current date (`yyyy-MM-dd`)
  - `WORLD_DIMENSION`: Groups by World, then by Dimension
  - `WORLD_DATE`: Groups by World, then by Date
  - `WORLD_DIMENSION_DATE`: Three-level hierarchy: World → Dimension → Date
  - `WORLD_DATE_DIMENSION`: Three-level hierarchy: World → Date → Dimension
  - `NONE`: Disables grouping (standard Minecraft behavior)
- World identification utility for detecting current world context
- Global and per-world configuration system using GSON
- JUnit 5 test framework support

### Contributors

- @scriptmunkeeofficial
- @google-labs-jules[bot]
