# Screenshot Manager (Fabric Mod)

## Project Overview

**Screenshot Manager** is a Minecraft Mod built for the **Fabric** loader. Its intended purpose is to manage screenshot
storage locations dynamically based on the current world or server (per-world basis).

* **Current State:** Prototype. The basic infrastructure is in place to intercept screenshot saving, but the dynamic
  per-world logic and configuration persistence are not yet implemented.
* **Language:** Java 21
* **Build System:** Gradle (using Fabric Loom)

## Architecture

### Entry Points

* **Main:** `com.milezerosoftware.mc.ModInitializerImpl` - Standard Fabric initialization.
* **Client:** `com.milezerosoftware.mc.ScreenshotManagerClient` - Client-side initialization.

### Core Logic

* **This is important: High-Level Project Architecture**
  Since your mod targets client-only features (screenshots/UI), almost all logic will live in the client source set,
  while your data models live in main.

| Package     | Package Path                            | Responsibility                                                      |
|-------------|-----------------------------------------|---------------------------------------------------------------------|
| Data Models | `com.milezerosoftware.mc.config`        | POJO classes for global and per-world settings.                     |
| Logic       | `com.milezerosoftware.mc.client.util`   | Extracting world data (coordinates, days) and PNG metadata writing. |
| Integration | `com.milezerosoftware.mc.client.mixin`  | Intercepting the screenshot process.                                |
| UI          | `com.milezerosoftware.mc.client.compat` | ModMenu and Cloth Config screens.                                   |

* **Mixin:** `com.milezerosoftware.mc.client.mixin.ScreenshotRecorderMixin`
  * Injects into `net.minecraft.client.util.ScreenshotRecorder.getScreenshotFilename`.
  * Currently redirects all screenshots to a path defined in `ModConfig` (defaulting to a subfolder).
* **Configuration:** `com.milezerosoftware.mc.config.ModConfig`
  * Currently a singleton stub.
  * **TODO:** Implement integration with Cloth Config (dependency is already added) and dynamic path resolution logic.

## Building and Running

The project uses the standard Gradle wrapper.

* **Build Mod:**

  ```bash
  ./gradlew build
  ```

  Artifacts will be in `build/libs/`.

* **Run Client:**

  ```bash
  ./gradlew runClient
  ```

* **Generate Sources (IDE):**

  ```bash
  ./gradlew genSources
  ```

## Testing

The project uses **JUnit 5** for unit testing.

* **Run Tests:**

  ```bash
  ./gradlew test
  ```

### Unit Testing Guidance

* **Minecraft Coupling:** Avoid direct interaction with Minecraft classes (e.g., `MinecraftClient`, `World`) in unit tests as they require a full game environment.
* **Decoupling:** Focus on testing logic that can be decoupled from Minecraft/Fabric specific APIs.
* **Mocks:** Use lightweight mocks to isolate logic from the game environment when necessary.
* **Prioritization:** Prioritize testing for utility classes, configuration logic, and data models.

## Dependencies

Defined in `build.gradle`:

* **Fabric API**
* **Cloth Config** (Configuration UI)
* **ModMenu** (Mod list integration)

## Key Files

* `src/main/resources/fabric.mod.json`: Mod metadata (ID: `screenshotmanager`).
* `src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`: Screenshot redirection logic.
* `src/main/java/com/milezerosoftware/mc/config/ModConfig.java`: Configuration holder.

## Development Notes

* **Per-World Logic:** The description claims per-world support, but the code currently only uses a static `customPath`.
  Logic needs to be added to detect the current world/server context.
* **Mixins:** Defined in `screenshotmanager.mixins.json` (common) and `screenshotmanager.client.mixins.json` (
  client-only).
