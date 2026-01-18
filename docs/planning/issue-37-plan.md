# Issue Resolution Plan: feat: Support Multiple Minecraft Versions

**Issue Link**: [https://github.com/milezerosoftware/screenshot-manager-enhanced/issues/37](https://github.com/milezerosoftware/screenshot-manager-enhanced/issues/37)
**Proposed Branch**: `git checkout -b issue-37`

## Issue Summary

The goal is to update the project to support building the Screenshot Manager Enhanced mod for multiple versions of Minecraft (1.21.11, 1.21.8, and 1.20.6) and potentially other loaders (NeoForge, Forge, Quilt). The current single-version Gradle setup is insufficient and would lead to code duplication.

## Affected Codebase Areas

**Common**, **Client**, **Assets/Data**

## Implementation Plan

This plan outlines the steps to restructure the project using the "Universal Mod Template" architecture, centralizing shared logic in a `common` module and using dynamic version properties.

### Prerequisites

- Ensure you are on the `main` branch and it's up-to-date.
- Create a new branch: `git checkout -b issue-37`

### Step-by-Step Implementation

1. **Step 1: Repo Restructure**
    - Create root directories: `common`, `fabric`, `buildSrc`, `versionProperties`.
    - **Verification**: Check that directories exist via `ls -d common/ fabric/ buildSrc/ versionProperties/`.
    - **CHECKPOINT**: STOP for User Commit.

2. **Step 2: BuildSrc Implementation**
    - Files to create: `buildSrc/build.gradle` (base plugins), `buildSrc/src/main/groovy/minecraft.gradle` (shared MC build logic), `buildSrc/src/main/groovy/fabric.gradle` (Fabric-specific logic).
    - **Verification**: Run `./gradlew :buildSrc:build` to ensure scripts compile.
    - **CHECKPOINT**: STOP for User Commit.

3. **Step 3: Settings Logic**
    - Files to modify: `settings.gradle`.
    - Changes needed: Implement dynamic inclusion logic to read `-Pmc_ver`, load the corresponding `.properties` file from `versionProperties/`, and include `:common` and `:fabric`.
    - **Verification**: Run `./gradlew projects` and check that `:common` and `:fabric` are listed.
    - **CHECKPOINT**: STOP for User Commit.

### 3. Build Logic

#### Artifact Naming

Jars must be named following the pattern: `${project_name}-${release_version}-${minecraft_version}.jar`.
Example: `screenshot-manager-enhanced-1.0.1-1.21.10.jar`.

#### `settings.gradle` Logic

The logic for loading version properties is inlined directly for maximum initialization robustness:

```groovy
def mcVer = settings.providers.gradleProperty("mc_ver").getOrElse(settings.providers.gradleProperty("minecraft_version").getOrElse("1.21.11"))
def propsFile = file("versionProperties/${mcVer}.properties")
// ... load and inject to gradle.beforeProject
```

#### `fabric/build.gradle`

Utilizes `fabric-loom` and maps common source:

```groovy
loom {
    splitEnvironmentSourceSets()
}
sourceSets.main.java.srcDirs += [project(":common").file("src/main/java")]
// ... etc
```

1. **Step 4: Code Migration**
    - Files to move: Move content of current `src/main/java`, `src/client/java`, and `src/main/resources` to `common/src/main/java` and `common/src/main/resources`.
    - Create `common/build.gradle` applying the `minecraft` plugin from `buildSrc`.
    - **Verification**: Check file locations via `ls -R common/src/main`.
    - **CHECKPOINT**: STOP for User Commit.

2. **Step 5: Fabric Project Setup**
    - Files to create: `fabric/build.gradle`.
    - Changes needed: Apply `fabric` plugin from `buildSrc`. Configure it to pull source and resources from the `:common` project as a thin wrapper.
    - **Verification**: Run `./gradlew :fabric:tasks` to ensure Fabric tasks are generated.
    - **CHECKPOINT**: STOP for User Commit.

3. **Step 6: Properties Configuration**
    - Files to create: `versionProperties/1.21.11.properties`, `versionProperties/1.21.8.properties`, `versionProperties/1.20.6.properties`.
    - Changes needed: Define versions for Minecraft, Fabric Loader, Fabric API, Cloth Config, and ModMenu for each target.
    - **Verification**: Verify files exist and contain correct version mappings.
    - **CHECKPOINT**: STOP for User Commit.

4. **Step 7: Final Verification & Testing**
    - **1.21.11**: Run `./gradlew :fabric:runClient -Pmc_ver=1.21.11`. Verify mod loads and screenshots are grouped.
    - **1.21.8**: Run `./gradlew :fabric:runClient -Pmc_ver=1.21.8`. Verify mod loads.
    - **1.20.6**: Run `./gradlew :fabric:runClient -Pmc_ver=1.20.6`. Verify mod loads.
    - **Verification**: Successful game launch and screenshot functionality across all three versions.
    - **CHECKPOINT**: Final User Commits for each verified version.

## Todo Checklist

- [x] Understand the issue thoroughly.
- [ ] Create a new Git branch: `git checkout -b issue-37`.
- [ ] Implement Step 1: Repo Restructure.
- [ ] Implement Step 2: BuildSrc Implementation.
- [ ] Implement Step 3: Settings Logic.
- [ ] Implement Step 4: Code Migration.
- [ ] Implement Step 5: Fabric Project Setup.
- [ ] Implement Step 6: Properties Configuration.
- [ ] Write/Update unit tests.
- [ ] Run all tests: `./gradlew test`.
- [ ] Build the application for all targets: `./gradlew build -Pmc_ver=[version]`.
- [ ] Verify the fix manually on 1.21.11, 1.21.8, and 1.20.6.
- [ ] Commit changes after each step with descriptive messages.
- [ ] Push the branch and create a Pull Request.

## Testing Strategy

- **Unit Tests**: Ensure existing JUnit 5 tests pass when run in the new multi-module environment.
- **Integration/E2E Tests**: Manual verification using `runClient` for each Minecraft version target.
- **Manual Verification**:
    1. Launch the game via Gradle for a specific version.
    2. Join a world/server.
    3. Take a screenshot using the Minecraft screenshot key.
    4. Confirm the screenshot is saved in the correct subfolder based on the current configuration.

## Potential Risks & Considerations

- **Risk**: Minecraft API changes between 1.20.6 and 1.21.11 might break compilation.
- **Mitigation**: Use version-specific adapters or conditional logic within `common` if necessary, or move conflicting files to version-specific subprojects.
- **Risk**: Complex Gradle configuration could be hard to debug.
- **Mitigation**: Follow the "Universal Mod Template" structure as a proven reference.
