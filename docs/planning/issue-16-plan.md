# Issue Resolution Plan: chore: Use world name for screenshot save path

**Issue Link**: <https://github.com/milezerosoftware/screenshot-manager/issues/16>
**Proposed Branch**: `git checkout -b issue-16`

## Issue Summary

Implement logic to dynamically determine the screenshot save path based on the current world or server. This leverages the `WorldUtils` class to segregate screenshots into subfolders (e.g., `screenshots/MyWorld/` or `screenshots/ServerIP/`).

## Affected Codebase Areas

- **Client**: `src/client/java/com/milezerosoftware/mc/client/util/WorldUtils.java`
- **Client**: `src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`

## Implementation Plan

### Prerequisites

- [x] Issue 15 global config cleanup is complete (customPath removed).
- Create a new branch: `git checkout -b issue-16`

### Step-by-Step Implementation

1. **Step 1: Enhance WorldUtils**
    - Files to modify: `src/client/java/com/milezerosoftware/mc/client/util/WorldUtils.java`
    - Changes needed:
        - Implement `getSafeWorldId()`:
            - Call `getWorldId()`.
            - Sanitize the result: replace `:` (common in IPv6) and other OS-illegal characters (`/`, `\`, `*`, `?`, `"`, `<`, `>`, `|`) with `_` or `-`.
            - Ensure the returned string is a valid directory name.

2. **Step 2: Implement Dynamic Path Logic in Mixin**
    - Files to modify: `src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`
    - Changes needed:
        - In `onGetScreenshotFilename`:
        - Call `WorldUtils.getSafeWorldId()` to get the subfolder name.
        - Construct the final path relative to the game directory: `new File(gameDir, "screenshots/" + safeWorldId)`.
        - Resulting structure: `.../minecraft/screenshots/{SafeWorldID}/{timestamp}.png`.
        - Ensure the directory exists (`mkdirs`).

## Todo Checklist

- [ ] Create a new Git branch `issue-16`.
- [ ] Implement `WorldUtils.getSafeWorldId()` with regex sanitization (e.g., `[^a-zA-Z0-9.-]`).
- [ ] Update `ScreenshotRecorderMixin` to use `getSafeWorldId()`.
- [ ] Run `gradle build` to verify compilation.
- [ ] Verify manually in Singleplayer (World name).
- [ ] Verify manually in Multiplayer (Server IP with/without port).
- [ ] Commit and Push.

## Testing Strategy

- **Manual Verification**:
  - Join SP world "TestWorld". Take screenshot. Verify folder `.../minecraft/screenshots/TestWorld/` is created and contains the image.
  - Join Server with IP `127.0.0.1`. Take screenshot. Verify folder `.../minecraft/screenshots/127.0.0.1/` is created and contains the image.

## Potential Risks & Considerations

- **Filesystem Limits**: Very long server addresses or world names might exceed OS path limits.
- **Special Characters**: Windows forbids `< > : " / \ | ? *`.
