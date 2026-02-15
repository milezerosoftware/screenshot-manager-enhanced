# Changelog

All notable changes to Screenshot Manager Enhanced will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed

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
