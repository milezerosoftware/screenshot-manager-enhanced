# Context: Screenshot Manager Enhanced

## Identity

You are a senior software engineer. You don't do anything halfass. You allow for high quality and precise implementation.

## Project Overview

**Screenshot Manager Enhanced** is a Minecraft Mod built for the **Fabric** loader (with multi-version support via Universal Mod architecture). Its purpose is to manage screenshot storage locations dynamically based on the current world or server (per-world basis), embed rich screenshot metadata, and provide an in-game screenshot gallery.

* **Current State:** Release 2.0.0+ / Active development (Minecraft 1.21.x and 26.x support).
* **Language:** Java 21 (baseline) & Java 25 (modern target versions).
* **Build System:** Gradle (Fabric Loom).
* **Mapping Baseline:** Mojang official mappings.

### Development Notes

* **Dynamic Path Resolution:** `ScreenshotPathGenerator` handles dynamic screenshot path generation based on global and per-world/per-server configurations and `GroupingMode`.
* **Mixins:** Interception logic is defined in `screenshot-manager-enhanced.mixins.json` (common) and `screenshot-manager-enhanced.client.mixins.json` (client-only).
* **UI & Integrations:** In-game screenshot gallery powered by `owo-lib`, ModMenu integration, Cloth Config UI, and native OS clipboard / trash handling.

## Development Rules

1. **Branching Strategy:** All new work must be in a branch based off the repository's default branch (`main`) or a dependent feature branch. No direct commits to `main`. Pull requests must target `main`.
2. **Step-by-Step Commits:** All implementation steps must conclude with a git commit that is explicitly approved by the developer.
3. **Mandatory Testing:** All implementations must be written in a testable manner with unit tests. Run `./gradlew :common:test` to verify before committing.

## Control Panel & Build Commands

Build commands use the dynamic property `-Pmc_ver` to target specific Minecraft versions (e.g. `1.21.11`, `26.1.2`, `26.2`).

* **Run Tests (Common module):**
  ```bash
  ./gradlew :common:test --no-daemon
  ```
* **Build Mod:**
  ```bash
  ./gradlew build -Pmc_ver=1.21.11 --no-daemon
  ```
* **Build All Fabric Targets:**
  ```bash
  ./gradlew buildAllFabric --no-daemon
  ```
* **Run Client (Fabric):**
  ```bash
  ./gradlew :fabric:runClient -Pmc_ver=1.21.11 --no-daemon
  ```
* **Generate Sources:**
  ```bash
  ./gradlew genSources --no-daemon
  ```

## Architecture & Key Files

### Multi-Module Structure

This project uses the [Universal Mod Template](https://github.com/thebuildcraft/Universal-Mod-Template) architecture to support multiple Minecraft versions with minimal code duplication.

* **`common/`**: Contains core logic, data models, GUI screens, mixins, and unit tests shared across all versions.
  * `common/src/main/java/`: Shared logic (config models, path generator, string sanitizers).
  * `common/src/client/java/`: Shared client features (gallery UI, clipboard, texture managers, mixin callbacks).
  * `common/src/client/java-21/` & `common/src/client/java-26/`: Version-specific bridge implementations (e.g., `WorldUtils`, `ModMenuIntegration`).
  * `common/src/test/java/`: JUnit 5 unit tests.
* **`fabric/`**: Loader-specific module applying `fabric-loom` and packaging the common source code into loader artifacts.
* **`buildSrc/`**: Custom Gradle logic, including dynamic version loading.
* **`versionProperties/`**: `.properties` files declaring dependencies (Minecraft, Fabric API, Cloth Config, ModMenu, owo-lib) per target Minecraft version.

### Key Components

| Component | Path (relative to `common/src/main/java` or `common/src/client/java`) | Responsibility |
|-----------|----------------------------------------------------------------------|----------------|
| Data Models | `...screenshotmanagerenhanced.config.*` | Config POJOs (`ModConfig`, `WorldConfig`, `GroupingMode`, `CustomPathConfig`). |
| Path Logic | `...screenshotmanagerenhanced.client.util.ScreenshotPathGenerator` | Path resolution based on active world/server and grouping rules. |
| Screenshot Interception | `...screenshotmanagerenhanced.client.mixin.ScreenshotRecorderMixin` | Injects into screenshot saving to route files to custom paths. |
| In-Game Gallery | `...screenshotmanagerenhanced.client.gui.screen.GalleryScreen` | Interactive screenshot browser using owo-lib UI components. |
| Metadata Handler | `...screenshotmanagerenhanced.client.util.MetadataHandler` | Embeds XMP metadata into captured screenshots. |

## Workflows & Planning

* **Implementation Plans:** Stored in `docs/planning/`. Plans serve as the single source of truth during feature development.
* **Release Changelogs:** Maintained in `CHANGELOG.md` adhering to Keep a Changelog.
