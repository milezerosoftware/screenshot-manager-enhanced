# Issue Resolution Plan: chore: update Github actions to only build if changes are made to java files

**Issue Link**: https://github.com/milezerosoftware/screenshot-manager/issues/20
**Proposed Branch**: `git checkout -b issue-20`

## Issue Summary
The goal is to optimize GitHub Actions usage by triggering the build and test workflow only when relevant files (Java source, Gradle configuration, resources) are modified. This reduces unnecessary CI runs for documentation-only or non-code changes, saving time and resources.

## Affected Codebase Areas
- **Assets/Data** (CI Configuration)

## Implementation Plan
This plan outlines the steps to resolve the issue by refining the workflow triggers.

### Prerequisites
- Ensure you are on the `main` branch and it's up-to-date.
- Create a new branch: `git checkout -b issue-20`

### Step-by-Step Implementation
1.  **Step 1: Update Workflow Triggers in `.github/workflows/build.yml`**
    -   Files to modify: `.github/workflows/build.yml`
    -   Changes needed:
        -   Update the `on` section for both `push` and `pull_request`.
        -   Add a `paths` list to specify which file patterns should trigger the workflow.
        -   **Patterns to include:**
            -   `src/**` (All source code and resources)
            -   `build.gradle` (Build dependencies/config)
            -   `gradle.properties` (Build properties)
            -   `settings.gradle` (Project settings)
            -   `gradle/wrapper/**` (Gradle wrapper updates)
            -   `.github/workflows/build.yml` (Changes to the workflow itself)
    -   **Verification**: Push a change to a file NOT in this list (e.g., a root-level `README.md` or `docs/`) and verify the workflow does *not* trigger. Then, push a change to a Java file and verify it *does* trigger.

## Todo Checklist
- [x] Understand the issue thoroughly.
- [x] Create a new Git branch `issue-20`.
- [x] Modify `.github/workflows/build.yml` to add `paths` filters.
- [x] Verify the configuration syntax is correct.
- [x] Commit changes with a descriptive message (e.g., "chore: optimize CI triggers to only run on code changes").
- [x] Push the branch and create a Pull Request.

## Testing Strategy
-   **Unit Tests**: N/A (Infrastructure change).
-   **Manual Verification**:
    1.  Create a branch.
    2.  Modify a markdown file in `docs/` and push -> **Expectation**: No build triggered.
    3.  Modify a `.java` file or `build.gradle` and push -> **Expectation**: Build triggered.

## Potential Risks & Considerations
-   **Risk**: If a file that *should* trigger a build is omitted from the `paths` list (e.g., a new config file type), CI might fail to catch breaking changes.
-   **Mitigation**: Include broad patterns like `src/**` to catch most development changes.
