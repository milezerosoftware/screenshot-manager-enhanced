# Changelog

All notable changes to Screenshot Manager Enhanced will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed

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
