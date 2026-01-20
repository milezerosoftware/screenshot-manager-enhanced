# Context: Screenshot Manager Enhanced

## Identity

You are a senior software engineer. You don't do anything halfass. You allow for high quality and precise implementation.

## Project Overview

**Screenshot Manager Enhanced** is a Minecraft Mod built for the **Fabric** loader. Its intended purpose is to manage screenshot storage locations dynamically based on the current world or server (per-world basis).

* **Current State:** Prototype. The basic infrastructure is in place to intercept screenshot saving. Dynamic per-world logic is being implemented, and GroupingMode expansion is complete.
* **Language:** Java 21
* **Build System:** Gradle (using Fabric Loom)

## Development Rules

1. **Branching Strategy:** All new work must be in a branch based off of `main` or a dependent feature branch. No direct commits to `main`.
2. **Step-by-Step Commits:** All implementation steps must conclude with a git commit that is explicitly approved by the developer.
3. **Mandatory Testing:** All implementations must be written in a way that they are testable via unit tests. There is no skipping out on testing the code!

### Development Notes

* **Per-World Logic:** The description claims per-world support, but the code currently only uses a static `customPath`. Logic needs to be added to detect the current world/server context.
* **Mixins:** Defined in `screenshot-manager-enhanced.mixins.json` (common) and `screenshot-manager-enhanced.client.mixins.json` (client-only).

## Control Panel

* **Build Mod**: `./gradlew build` (Artifacts in `build/libs/`)
* **Run Client**: `./gradlew runClient`
* **Run Tests**: `./gradlew test`
* **Generate Sources**: `./gradlew genSources`

## Workflows

The following workflows are defined in `.gemini/commands`:

* **Implement**: `gemini implement` (`implement.toml`)
* **Planning**:
  * New Plan: `gemini planning new` (`planning/new.toml`)
* **Issues**:
  * Create Issue: `gemini issue create` (`issue/create.toml`)
  * Resolve Issue: `gemini issue resolve` (`issue/resolve.toml`)
* **Pull Requests**:
  * Create PR: `gemini pr create` (`pr/create.toml`)

## Architecture & Key Files

### Multi-Module Structure

This project uses a "Universal Mod" architecture to support multiple Minecraft versions and loaders with minimal code duplication.

* **`common/`**: Contains the core logic, data models, and mixins that are shared across all versions and loaders.
* **`fabric/`**: A loader-specific module that applies the `fabric-loom` plugin and wraps the `common` source code.
* **`buildSrc/`**: Contains custom Gradle logic, including the `VersionLoader` utility.
* **`versionProperties/`**: Contains `.properties` files that define dependency versions (Minecraft, Fabric API, etc.) for each target version.

### Key Components

| Component | Path (relative to `common/src/main/java`) | Responsibility |
|-----------|------------------------------------------|----------------|
| Data Models | `...screenshotmanagerenhanced.config` | POJO classes for global and per-world settings. |
| Path Logic | `...screenshotmanagerenhanced.client.util` | `ScreenshotPathGenerator` handles all path resolution logic. |
| Integration | `...screenshotmanagerenhanced.client.mixin` | Intercepts the screenshot saving process via `ScreenshotRecorderMixin`. |

## Building and Running

The build system is dynamic. Use the `-Pmc_ver` property to target a specific Minecraft version.

### Build All Support

* `buildAllFabric`: Builds all configured Fabric versions.
* `buildAllAll`: Builds all configured loaders and versions.

#### Future Build Support (Targets)

* `buildAllNeoForge`: (Roadmap)
* `buildAllForge`: (Roadmap)
* `buildAllQuilt`: (Roadmap)

### Standard Commands

  ```

* **Run Client:**

  ```bash
  ./gradlew :fabric:runClient -Pmc_ver=1.21.10
  ```

## Testing

The project uses **JUnit 5** for unit testing, located in `common/src/test/java`.

* **Run Tests:**

  ```bash
  ./gradlew :common:test
  ```

## Dependencies

Dependencies are managed dynamically via `versionProperties/*.properties`. Key libraries include:

* **Fabric API**
* **Cloth Config** (Configuration UI)
* **ModMenu** (Mod list integration)

## Key Files

* `settings.gradle`: Handles dynamic project inclusion and property loading.
* `common/src/main/resources/fabric.mod.json`: Mod metadata.
* `common/src/client/java/.../ScreenshotRecorderMixin.java`: Interception logic.
* `common/src/main/java/.../ScreenshotPathGenerator.java`: Core path logic.
