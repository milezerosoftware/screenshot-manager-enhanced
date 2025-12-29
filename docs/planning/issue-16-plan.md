# Issue Resolution Plan: chore: Use world name for screenshot save path

**Issue Link**: https://github.com/milezerosoftware/screenshot-manager/issues/16
**Proposed Branch**: `git checkout -b issue-16`

## Issue Summary
Implement logic to dynamically determine the screenshot save path based on the current world or server. This leverages the `WorldUtils` class to segregate screenshots into subfolders (e.g., `screenshots/MyWorld/`).

## Affected Codebase Areas
- **Client**: `ScreenshotRecorderMixin.java`, `WorldUtils.java`.

## Implementation Plan

### Prerequisites
- Ensure Issue 15 changes are merged or present (this builds upon the removal of the global path).
- Create a new branch: `git checkout -b issue-16`

### Step-by-Step Implementation
1.  **Step 1: Enhance WorldUtils for Safety**
    -   Files to modify: `src/client/java/com/milezerosoftware/mc/client/util/WorldUtils.java`
    -   Changes needed: Add a `getSafeWorldId()` method (or update `getWorldId`) that ensures the returned string is safe for use as a directory name (sanitizing colons, slashes, etc., especially for IPv6 addresses in multiplayer).
    -   **Verification**: Unit tests or manual testing with weird server names/IPs.

2.  **Step 2: Implement Dynamic Path Logic in Mixin**
    -   Files to modify: `src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`
    -   Changes needed:
        -   Call `WorldUtils.getSafeWorldId()` to get the subfolder name.
        -   (Optional) Check `ModConfig` to see if a specific `WorldConfig` override exists for this ID (future-proofing).
        -   Construct the final path: `screenshots/{WorldID}/`.
        -   Ensure the directory exists (`mkdirs`).
    -   **Verification**: taking a screenshot in Singleplayer creates a folder matching the world name.

## Todo Checklist
- [ ] Understand the issue thoroughly.
- [ ] Create a new Git branch `issue-16`.
- [ ] Update `WorldUtils` to provide filesystem-safe world identifiers.
- [ ] Update `ScreenshotRecorderMixin` to use the dynamic world path.
- [ ] Run `gradle build`.
- [ ] Verify manually in Singleplayer.
- [ ] Verify manually in Multiplayer (if possible, or mock).
- [ ] Commit and Push.

## Testing Strategy
-   **Manual Verification**:
    -   Join SP world "TestWorld". Take screenshot. Check if `screenshots/TestWorld/` exists.
    -   Join Server. Take screenshot. Check if `screenshots/{ServerIP}/` exists.

## Potential Risks & Considerations
-   **Filesystem Limits**: Very long server addresses or world names might exceed OS path limits.
-   **Special Characters**: Windows forbids `< > : " / \ | ? *`.
