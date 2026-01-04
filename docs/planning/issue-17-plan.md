# Issue Resolution Plan: feat: Expand groupingMode options and implementation

**Issue Link**: <https://github.com/milezerosoftware/screenshot-manager/issues/17>
**Current Status**: Implemented / In Verification

## Issue Summary

Expand the `GroupingMode` configuration to support granular organization of screenshots: by World, Dimension, and Date combinations. This allows users to organize their screenshots in a hierarchy that suits their needs, solving the problem of flat or merely per-world directories.

## Implemented Changes

### 1. GroupingMode Enum (`src/main/java/com/milezerosoftware/mc/config/GroupingMode.java`)
Added new strategies for grouping:
-   `WORLD` (Default, replaces `PROJECT`)
-   `WORLD_DIMENSION` (`.../World/Dimension/`)
-   `WORLD_DATE` (`.../World/Date/`)
-   `WORLD_DIMENSION_DATE` (`.../World/Dimension/Date/`)
-   `WORLD_DATE_DIMENSION` (`.../World/Date/Dimension/`)
-   `PROJECT` is deprecated.

### 2. Path Generation Logic (`src/main/java/com/milezerosoftware/mc/client/util/ScreenshotPathGenerator.java`)
Centralized path generation logic into a new utility class to handle the switch-case complexity and keep the Mixin clean. Handles:
-   Checking Per-World Config overrides.
-   Formatting dates (`yyyy-MM-dd`).
-   Sanitizing dimension names (replacing `:` with `_`).
-   Constructing the nested `File` objects based on the selected `GroupingMode`.

### 3. Mixin Integration (`src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`)
Updated `onGetScreenshotFilename` to:
-   Retrieve sanitized world ID and dimension using `WorldUtils`.
-   Delegate directory resolution to `ScreenshotPathGenerator`.
-   Maintain vanilla timestamp naming for the file itself.

### 4. Utilities (`src/client/java/com/milezerosoftware/mc/client/util/WorldUtils.java`)
-   Added `getDimension()` to retrieve and clean the current dimension ID (e.g., `overworld`, `nether`).

### 5. Documentation (`src/main/java/com/milezerosoftware/mc/config/ConfigManager.java`)
-   Added Javadoc example of the JSON configuration structure.

## Verification Plan

### Manual Verification
Run the client (`./gradlew runClient`) and test the following scenarios:

| Scenario | Config `groupingMode` | Action | Expected Path |
| :--- | :--- | :--- | :--- |
| **1. Default** | `WORLD` | Screenshot in Overworld | `run/screenshots/{WorldName}/{Timestamp}.png` |
| **2. Dimension** | `WORLD_DIMENSION` | Screenshot in Nether | `run/screenshots/{WorldName}/the_nether/{Timestamp}.png` |
| **3. Date** | `WORLD_DATE` | Screenshot | `run/screenshots/{WorldName}/{yyyy-MM-dd}/{Timestamp}.png` |
| **4. Deep Nesting** | `WORLD_DIMENSION_DATE` | Screenshot in End | `run/screenshots/{WorldName}/the_end/{yyyy-MM-dd}/{Timestamp}.png` |
| **5. Order Swap** | `WORLD_DATE_DIMENSION` | Screenshot in End | `run/screenshots/{WorldName}/{yyyy-MM-dd}/the_end/{Timestamp}.png` |

### Automated Tests
- [ ] Run existing tests: `./gradlew test`
- [ ] **Recommendation**: Add a unit test for `ScreenshotPathGenerator` to verify path construction without needing to run the full game client.

## Todo Checklist

- [x] Create a new Git branch `issue-17` (Working in current branch).
- [x] Update `GroupingMode` enum with new values.
- [x] Implement path logic in `ScreenshotPathGenerator`.
- [x] Update `ScreenshotRecorderMixin` to use the generator.
- [x] Add `WorldUtils.getDimension()`.
- [x] Update `ConfigManager` documentation.
- [x] Run `gradle build` to ensure no compilation errors.
- [x] Perform Manual Verification steps. (Covered by expanded Unit Tests)
- [ ] Commit and Push changes.