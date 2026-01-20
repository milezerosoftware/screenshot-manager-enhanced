# Issue Resolution Plan: feat: [Enhancement] Path Display in Notification

**Issue Link**: <https://github.com/milezerosoftware/screenshot-manager-enhanced/issues/8>
**Proposed Branch**: `issue-8`

## Issue Summary

The goal is to improve the user experience by displaying the relative path (e.g., `my_world/2026-01-18.png`) in the screenshot success notification instead of just the filename. This is particularly useful when screenshots are grouped into subfolders. The feature should be toggleable via the config.

## Affected Codebase Areas

- **Common**: `ModConfig`, `ScreenshotRecorderMixin`
- **Client**: `ModMenuIntegration`

## Implementation Plan

### Prerequisites

- [ ] Implement on a new branch: `issue-8`

### Global Constraint

> [!IMPORTANT]
> **Each step below must be followed by a git commit to the branch `issue-8` and explicit user confirmation before proceeding to the next step.**

### Step-by-Step Implementation

1. **Step 1: Update Configuration Data Model**
    - **File**: `common/src/main/java/com/milezerosoftware/mc/screenshotmanagerenhanced/config/ModConfig.java`
    - **Action**: Add a new boolean field `displayRelativePath` (default: `true`).
    - **Verification**: Check that the field exists and initializes correctly.
    - **Commit**: `feat: add displayRelativePath config option`

2. **Step 2: Update ModMenu Configuration Screen**
    - **File**: `common/src/client/java/com/milezerosoftware/mc/screenshotmanagerenhanced/client/compat/ModMenuIntegration.java`
    - **Action**: Add a `startBooleanToggle` (checkbox/button) for "Display Relative Path" in the "General" category. Using `startBooleanToggle` ensures it renders as a toggleable option.
    - **Verification**: Launch the client, open Mod Menu -> Screenshot Manager Enhanced -> Configure, and verify the toggle appears as a checkbox/button and saves correctly.
    - **Commit**: `feat: add UI toggle for displayRelativePath`

3. **Step 3: Extract Path Display Logic**
    - **File**: `common/src/main/java/com/milezerosoftware/mc/screenshotmanagerenhanced/client/util/ScreenshotPathGenerator.java`
    - **Action**:
        - Create a new public static method: `String getScreenshotNotificationText(File screenshotFile, File screenshotsDir, boolean displayRelativePath)`.
        - **Logic**:
            - If `displayRelativePath` is false, return `screenshotFile.getName()`.
            - If true, calculate the path relative to `screenshotsDir`. This effectively captures the grouping folders (e.g., `MyWorld/2025-01-01/shot.png`) plus the filename.
            - Return the formatted string.
    - **Verification**: This logic will be verified by unit tests in the next step.
    - **Commit**: `feat: implement screenshot notification text logic`

4. **Step 4: Implement Unit Tests**
    - **File**: `common/src/test/java/com/milezerosoftware/mc/screenshotmanagerenhanced/client/util/ScreenshotPathGeneratorTest.java` (Create if needed)
    - **Action**:
        - Add tests for `getScreenshotNotificationText`.
        - **Test Case 1**: `displayRelativePath = false`. Expect just filename.
        - **Test Case 2**: `displayRelativePath = true`, file inside a subfolder. Expect `subfolder/filename.png`.
        - **Test Case 3**: `displayRelativePath = true`, file directly in screenshots dir. Expect `filename.png`.
    - **Verification**: Run `./gradlew :common:test` and ensure tests pass.
    - **Commit**: `test: add unit tests for notification text logic`

5. **Step 5: Implement Notification Logic in Mixin**
    - **File**: `common/src/client/java/com/milezerosoftware/mc/screenshotmanagerenhanced/client/mixin/ScreenshotRecorderMixin.java`
    - **Action**:
        - Inject into `net.minecraft.client.util.ScreenshotRecorder.saveScreenshot`.
        - Use `@ModifyArg` on the `messageReceiver.accept()` call to intercept the `Text` message.
        - Call `ScreenshotPathGenerator.getScreenshotNotificationText` using the text's click event file path (if available) or the file object if accessible in context.
        - Update the text component with the new display string while preserving the click event (opening the file).
    - **Verification**: Verify manually in-game as detailed below.
    - **Commit**: `feat: apply relative path logic to screenshot notification`

### Verification Plan

#### Automated Tests

- **Unit Tests**: Run `./gradlew :common:test`.
  - Verify `ScreenshotPathGeneratorTest` passes, confirming the path display logic handles nested folders and the config toggle correctly off-game.

#### Manual Verification

1. **Default Behavior**:
    - Set Grouping Mode to `WORLD`.
    - Enter a world (e.g., "New World").
    - Take a screenshot.
    - **Expect**: Chat says "Saved screenshot as [New World/2026-...png]".
2. **Configuration Toggle**:
    - Go to Mod Config, disable "Display Relative Path".
    - Take a screenshot.
    - **Expect**: Chat says "Saved screenshot as [2026-...png]" (Vanilla behavior).
3. **Clickability**:
    - Click the path in the chat.
    - **Expect**: The screenshot file opens.

## Todo Checklist

- [ ] Create branch `issue-8`.
- [ ] Add `displayRelativePath` to `ModConfig`.
- [ ] Add boolean toggle to `ModMenuIntegration`.
- [ ] Implement `getScreenshotNotificationText` in `ScreenshotPathGenerator`.
- [ ] Write unit tests for `getScreenshotNotificationText`.
- [ ] Implement `ModifyArg` mixin in `ScreenshotRecorderMixin` using the tested logic.
- [ ] Verify manually in-game.
- [ ] Run `./gradlew build` to ensure no regressions.
