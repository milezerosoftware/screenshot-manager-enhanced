# Issue Resolution Plan: feat: Expand groupingMode options and implementation

**Issue Link**: https://github.com/milezerosoftware/screenshot-manager/issues/17
**Proposed Branch**: `git checkout -b issue-17`

## Issue Summary
Add support for granular screenshot grouping: by World, Dimension, and Date combinations.

## Affected Codebase Areas
- **Common/Data**: `GroupingMode.java`.
- **Client**: `ScreenshotRecorderMixin.java`.

## Implementation Plan

### Prerequisites
- Depends on Issue 16 (for World/Dimension detection).
- Create a new branch: `git checkout -b issue-17`

### Step-by-Step Implementation
1.  **Step 1: Update Enum**
    -   Files to modify: `src/main/java/com/milezerosoftware/mc/config/GroupingMode.java`
    -   Changes needed: Add values: `WORLD`, `WORLD_DATE`, `WORLD_DIMENSION`, `WORLD_DATE_DIMENSION`.

2.  **Step 2: Implement Grouping Logic**
    -   Files to modify: `src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`
    -   Changes needed:
        -   In `onGetScreenshotFilename`, switch on `ModConfig.getInstance().groupingMode`.
        -   Use `WorldUtils.getWorldId()`, `WorldUtils.getDimension()`, and current date to construct paths.
        -   Example: `screenshots/{World}/{Dimension}/{Date}/{File}`.
    -   **Verification**: Change config mode and verify folder structure on screenshot.

## Todo Checklist
- [ ] Understand the issue thoroughly.
- [ ] Create a new Git branch `issue-17`.
- [ ] Add new enum values to `GroupingMode.java`.
- [ ] Implement switch logic in `ScreenshotRecorderMixin.java`.
- [ ] Run `gradle build`.
- [ ] Manual verification of each mode.
- [ ] Commit and Push.

## Testing Strategy
-   **Manual**: Cycle through each new `GroupingMode`, take a screenshot, and verify the resulting directory tree matches the expectation.

## Potential Risks & Considerations
-   **Path Depth**: `World/Date/Dimension` can create deep structures.
-   **User Confusion**: Clear documentation in `ModConfig` comments is required.
