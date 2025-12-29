# Issue Resolution Plan: chore: Refactor customPath to WorldConfig

**Issue Link**: https://github.com/milezerosoftware/screenshot-manager/issues/15
**Proposed Branch**: `git checkout -b issue-15`

## Issue Summary
The global `customPath` setting in `ModConfig` creates ambiguity and conflicts with the intended per-world configuration architecture. This task removes the global field, strictly delegating path configuration to the per-world `WorldConfig` objects.

## Affected Codebase Areas
- **Common/Data**: `ModConfig.java` (removal of field).
- **Client**: `ScreenshotRecorderMixin.java` (cleanup of broken usage).

## Implementation Plan

### Prerequisites
- Ensure you are on the `main` branch and it's up-to-date.
- Create a new branch: `git checkout -b issue-15`

### Step-by-Step Implementation
1.  **Step 1: Update Data Model**
    -   Files to modify: `src/main/java/com/milezerosoftware/mc/config/ModConfig.java`
    -   Changes needed: Remove the `public String customPath = "screenshots";` field.
    -   **Verification**: Run `gradle build` -> Expect compilation errors in `ScreenshotRecorderMixin`.

2.  **Step 2: Update Mixin (Provisional Fix)**
    -   Files to modify: `src/client/java/com/milezerosoftware/mc/client/mixin/ScreenshotRecorderMixin.java`
    -   Changes needed: Replace the reference to `config.customPath` with a hardcoded fallback string `"screenshots"` (or a temporary default constant).
    -   **Note**: Dynamic path resolution will be fully implemented in Issue 16. This step ensures the project compiles and maintains basic functionality (saving to default folder) during the transition.
    -   **Verification**: Run `gradle build` -> Expect success.

## Todo Checklist
- [ ] Understand the issue thoroughly.
- [ ] Create a new Git branch `issue-15`.
- [ ] Remove `customPath` from `ModConfig.java`.
- [ ] Update `ScreenshotRecorderMixin.java` to use a temporary default path.
- [ ] Run `gradle build` to verify compilation.
- [ ] Verify functionality manually (screenshots should save to `screenshots/`).
- [ ] Commit changes.
- [ ] Push branch and create PR.

## Testing Strategy
-   **Manual Verification**: Launch the client, take a screenshot, and verify it saves to the default `screenshots` folder.
-   **Build**: Ensure the project compiles without the removed field.

## Potential Risks & Considerations
-   **Breaking Change**: Existing global config settings will be lost. This is intended but should be noted.
-   **Interim State**: Between Issue 15 and 16, users will temporarily lose custom path ability.
