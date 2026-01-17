# Context: Screenshot Manager Enhanced

## Identity

You are a senior software engineer. You don't do anything halfass. You allow for high quality and precise implementation.

## Project Overview

**Screenshot Manager Enhanced** is a Minecraft Mod built for the **Fabric** loader. Its intended purpose is to manage screenshot storage locations dynamically based on the current world or server (per-world basis).

* **Current State:** Prototype. The basic infrastructure is in place to intercept screenshot saving. Dynamic per-world logic is being implemented, and GroupingMode expansion is complete.
* **Language:** Java 21
* **Build System:** Gradle (using Fabric Loom)

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

### Entry Points

* **Main:** `com.milezerosoftware.mc.screenshotmanagerenhanced.ModInitializerImpl` - Standard Fabric initialization.
* **Client:** `com.milezerosoftware.mc.screenshotmanagerenhanced.ScreenshotManagerClient` - Client-side initialization.

### Core Logic

Since your mod targets client-only features (screenshots/UI), almost all logic will live in the client source set, while your data models live in main.

| Package     | Package Path                            | Responsibility                                                      |
|-------------|-----------------------------------------|---------------------------------------------------------------------|
| Data Models | `com.milezerosoftware.mc.screenshotmanagerenhanced.config`        | POJO classes for global and per-world settings.                     |
| Logic       | `com.milezerosoftware.mc.screenshotmanagerenhanced.client.util`   | Extracting world data (coordinates, days) and PNG metadata writing. |
| Integration | `com.milezerosoftware.mc.screenshotmanagerenhanced.client.mixin`  | Intercepting the screenshot process.                                |
| UI          | `com.milezerosoftware.mc.screenshotmanagerenhanced.client.compat` | ModMenu and Cloth Config screens.                                   |

* **Mixin:** `com.milezerosoftware.mc.screenshotmanagerenhanced.client.mixin.ScreenshotRecorderMixin`
  * Injects into `net.minecraft.client.util.ScreenshotRecorder.getScreenshotFilename`.
  * Currently a singleton stub.
  * **TODO:** Implement integration with Cloth Config (dependency is already added) and dynamic path resolution logic.

### Key Files

* `src/main/resources/fabric.mod.json`: Mod metadata (ID: `screenshot-manager-enhanced`).
* `src/client/java/com/milezerosoftware/mc/screenshotmanagerenhanced/client/mixin/ScreenshotRecorderMixin.java`: Screenshot redirection logic.
* `src/main/java/com/milezerosoftware/mc/screenshotmanagerenhanced/config/ModConfig.java`: Configuration holder.

## Coding Guidelines

### Unit Testing Guidance

* **Minecraft Coupling:** Avoid direct interaction with Minecraft classes (e.g., `MinecraftClient`, `World`) in unit tests as they require a full game environment.
* **Decoupling:** Focus on testing logic that can be decoupled from Minecraft/Fabric specific APIs.
* **Mocks:** Use lightweight mocks to isolate logic from the game environment when necessary.
* **Prioritization:** Prioritize testing for utility classes, configuration logic, and data models.

### Dependencies

* **Fabric API**
* **Cloth Config** (Configuration UI)
* **ModMenu** (Mod list integration)

## Memories
