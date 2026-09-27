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
4. **Version Upgrade Dependency Gate:** Before writing code or branches for a new Minecraft version, the agent MUST verify that all 5 core dependencies (Fabric Loader, Fabric API, Cloth Config, ModMenu, and owo-lib) have published an official artifact specifically targeting `<mc_ver>`. If ANY dependency is missing (notably `owo-lib`), the agent MUST NOT begin implementation and MUST flag the version upgrade as ON HOLD.
5. **Bridge Reusability & Anti-Duplication:** The agent MUST NOT create a new `common/src/client/java-<ver>` directory unless underlying library/vanilla API signatures have broke incompatibly. The agent must first inspect API diffs and reuse `ui_version=<existing>`. For minor method moves (e.g. `openPath`), implement adaptive runtime reflection/dispatch in `ScreenUtils` rather than duplicating files.

## Control Panel & Build Commands

Build commands use the dynamic property `-Pmc_ver` to target specific Minecraft versions (e.g. `1.21.11`, `26.1.2`, `26.2`, `26.3`).

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

## Minecraft Version Upgrade Protocol (AI Agent Execution)

When an AI agent is requested to support a new Minecraft version (`<mc_ver>`), the agent MUST execute the following deterministic protocol:

### Step 1: Pre-Flight Dependency Verification (Hard Gate)
Check remote Maven / Meta repositories for all 5 core dependencies:
1. `Fabric Loader`: `https://meta.fabricmc.net/v2/versions/loader/<mc_ver>`
2. `Fabric API`: `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml` (must contain `+<mc_ver>`)
3. `Cloth Config`: `https://maven.shedaniel.me/me/shedaniel/cloth/cloth-config-fabric/maven-metadata.xml` (must contain `<mc_ver>.x`)
4. `ModMenu`: `https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/maven-metadata.xml` (must support `<mc_ver>`)
5. `owo-lib`: `https://maven.wispforest.io/releases/io/wispforest/owo-lib/maven-metadata.xml` (must contain `+<mc_ver>`)

**Failure Rule:** If any dependency (specifically `owo-lib`) is missing an artifact explicitly targeting `<mc_ver>`:
- **DO NOT** create a full feature implementation.
- **DO NOT** bundle incompatible or mismatched versions into releases.
- Open a Draft PR titled `feat: add Minecraft <mc_ver> support [HOLD: Awaiting <dep> <mc_ver>]`.
- Report the blocker to the developer and halt further code execution.

### Step 2: Bridge Audit & Reusability Determination
- Compare vanilla/library API changes between previous supported version and `<mc_ver>`.
- If signatures and types are identical, configure `versionProperties/<mc_ver>.properties` with `ui_version=<existing_ui_version>`.
- If method movements occurred (e.g. `openPath`), implement adaptive runtime detection in existing bridge classes (`ScreenUtils`) to support both old and new targets.
- Only create a new `common/src/client/java-<ui_version>` directory if breaking changes cannot be reconciled via adaptive bridge helpers.

### Step 3: Local Verification Sequence
Run all 3 verification commands in order:
```bash
# 1. Unit tests for new version
./gradlew :common:test -Pmc_ver=<mc_ver> --no-daemon

# 2. Fabric build for new version
./gradlew :fabric:build -Pmc_ver=<mc_ver> --no-daemon

# 3. Matrix build for all active versions
./gradlew buildAllFabric --no-daemon
```

### Step 4: Documentation Synchronization
- Update `README.md` example commands and supported version note block.
- Update `AGENTS.md` version list.
- Add an `[Unreleased]` entry to `CHANGELOG.md`.
