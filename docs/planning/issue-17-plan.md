# Issue Resolution Plan: feat: Expand groupingMode options and implementation

**Issue Link**: <https://github.com/milezerosoftware/screenshot-manager/issues/17>
**Proposed Branch**: `git checkout -b issue-17`

## Issue Summary

Add support for granular screenshot grouping: by World, Dimension, and Date combinations. This allows users to organize their screenshots in a hierarchy that suits their needs.

## Affected Codebase Areas

- **Common/Data**: `src/main/java/com/milezerosoftware/mc/config/GroupingMode.java`
- **Client**: `src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`

## Implementation Plan

### Prerequisites

- [ ] Issue 16 must be completed (provides `WorldUtils.getSafeWorldId()`).
- Create a new branch: `git checkout -b issue-17`

### Step-by-Step Implementation

1. **Step 1: Update GroupingMode Enum**
    - Files to modify: `src/main/java/com/milezerosoftware/mc/config/GroupingMode.java`
    - Changes needed:
        - Retain `DATE`, `PROJECT` (alias for World?), `NONE`.
        - Add new modes for explicit granular control: `WORLD`, `WORLD_DATE`, `WORLD_DIMENSION`, `WORLD_DATE_DIMENSION`.
        - Consider deprecating `PROJECT` in favor of `WORLD` for clarity.

2. **Step 2: Implement Grouping Logic in Mixin**
    - Files to modify: `src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`
    - Changes needed:
        - Retrieve `ModConfig.getInstance().groupingMode`.
        - Construct the directory path relative to the game directory (e.g., `new File(gameDir, "screenshots/...")`):
            - `NONE`: `screenshots/` (Vanilla behavior)
            - `DATE`: `screenshots/{yyyy-MM-dd}/`
            - `WORLD`: `screenshots/{SafeWorldID}/`
            - `WORLD_DIMENSION`: `screenshots/{SafeWorldID}/{Dimension}/`
            - `WORLD_DATE`: `screenshots/{SafeWorldID}/{yyyy-MM-dd}/`
            - `WORLD_DATE_DIMENSION`: `screenshots/{SafeWorldID}/{yyyy-MM-dd}/{Dimension}/`
    - **Note**: Use `WorldUtils.getDimension()` for dimension names (sanitize if necessary).

## Todo Checklist

- [ ] Create a new Git branch `issue-17`.
- [ ] Update `GroupingMode` enum with new values.
- [ ] Implement switch case logic in `ScreenshotRecorderMixin`.
- [ ] Run `gradle build`.
- [ ] Verify each mode manually.
- [ ] Commit and Push.

## Verification Plan

### Manual Verification

- **Test Matrix**:
    1. Set `groupingMode = WORLD` -> Take screenshot -> Check `.../screenshots/MyWorld/{timestamp}.png`.
    2. Set `groupingMode = WORLD_DIMENSION` -> Go to Nether -> Take screenshot -> Check `.../screenshots/MyWorld/minecraft_the_nether/{timestamp}.png`.
    3. Set `groupingMode = DATE` -> Check `.../screenshots/2025-12-29/{timestamp}.png`.

### Automated Tests

- Consider adding a unit test for the path generation logic if extracted to a helper method (e.g., `ScreenshotUtils.getScreenshotDir(mode, worldId, dimension, date)`).
