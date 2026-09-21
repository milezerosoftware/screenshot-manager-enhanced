# Release Guide

## TL;DR Quick Reference

``` text
┌──────────────────────────────────────────────────────────────┐
│                    TWO-PHASE RELEASE WORKFLOW                │
├──────────────────────────────────────────────────────────────┤
│  PHASE 1: PREPARATION (on feature/main)                      │
│  1. ./gradlew prepareRelease                                 │
│     - Auto-detects version bump (SemVer)                     │
│     - Synthesizes changelog from git commits                 │
│     - Creates release/vX.Y.Z branch & commits version        │
│     - Pushes and opens GitHub Pull Request targeting main    │
│  2. Review and merge the Pull Request on GitHub              │
│                                                              │
│  PHASE 2: PUBLISHING (on main after merge)                   │
│  3. git checkout main && git pull                            │
│  4. ./gradlew publishRelease                                 │
│     - Builds release artifacts for all MC versions           │
│     - Tags git release and pushes tag                        │
│     - Creates GitHub Release in DRAFT with JARs attached     │
│     - Uploads all artifacts to Modrinth via API              │
│     - Promotes GitHub Release from Draft to Published        │
│                                                              │
│  SAFE TESTING:                                               │
│  Run any step with -PdryRun=true to simulate safely!         │
└──────────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Release Instructions

### Phase 1: Prepare Release (`prepareRelease`)

Run the guided preparation task:

```bash
# Dry-run first if you want to inspect without creating branches or PRs:
./gradlew prepareRelease -PdryRun=true

# Real execution:
./gradlew prepareRelease
```

The task will interactively:
1. Detect unreleased commits since the last release tag.
2. Propose the next semantic version (`Major`, `Minor`, or `Patch`) with manual override option.
3. Automatically categorize commits into Keep a Changelog format (`### Added`, `### Changed`, `### Fixed`, `### Internal`).
4. Checkout a `release/vX.Y.Z` branch, update `gradle.properties` and `CHANGELOG.md`, commit, and push.
5. Open a GitHub Pull Request to `main`.

### Merging the PR

Review the Pull Request on GitHub and merge it into `main`. **No commits are made directly to `main`**, keeping branch protections completely intact.

### Phase 2: Publish Release (`publishRelease`)

Once the PR is merged:

```bash
# 1. Switch to main and pull the merged changes
git checkout main
git pull

# 2. (Optional) Run in dry-run mode to verify all gates without publishing
./gradlew publishRelease -PdryRun=true

# 3. Run the live release
./gradlew publishRelease
```

The task will:
1. Verify working tree is clean and on `main`.
2. Extract the approved changelog notes for this version.
3. Build release JARs for all configured Minecraft versions via `./gradlew buildAllFabric`.
4. Create the git tag and push it to origin.
5. Create a **GitHub Release in DRAFT mode** and attach all generated JARs.
6. Publish all JARs directly to **Modrinth** using their v2 API.
7. Flip the GitHub Release from **Draft** to **Published**.

---

## Dependency Configuration

Dependencies are pre-configured in the workflow and applied to every upload:

| Dependency | Type |
|------------|------|
| `fabric-api` | Required |
| `cloth-config` | Required |
| `modmenu` | Optional |

To modify dependencies, edit `.github/workflows/release.yml`:

```yaml
dependencies: |
  fabric-api(required)
  cloth-config(required)
  modmenu(optional)
```

---

## Version Naming

Each platform receives consistently named versions:

| Platform | Format | Example |
|----------|--------|---------|
| Modrinth | `version+mc_version` | `1.2.0+1.21.10` |
| CurseForge | Display name | `Screenshot Manager Enhanced v1.2.0 for MC 1.21.10` |
| GitHub | Tag-based | `v1.2.0` |

---

## One-Time Setup

### GitHub Secrets

Before your first release, add these secrets to your repository:

1. Go to **Settings → Secrets and variables → Actions**
2. Add the following secrets:

| Secret Name | Where to Get |
|-------------|--------------|
| `MODRINTH_TOKEN` | [modrinth.com/settings/account](https://modrinth.com/settings/account) — Create token with `CREATE_VERSION` scope |
| `CURSEFORGE_TOKEN` | [curseforge.com/account/api-tokens](https://curseforge.com/account/api-tokens) |

### CurseForge Project ID

When ready to publish to CurseForge:

1. Get your numeric project ID from your CurseForge project URL
2. Edit `.github/workflows/release.yml`
3. Uncomment the CurseForge section and add your ID:

```yaml
curseforge-id: YOUR_PROJECT_ID
curseforge-token: ${{ secrets.CURSEFORGE_TOKEN }}
```

---

## Troubleshooting

### If a platform upload fails

1. Check GitHub Actions logs for the specific error
2. mc-publish has built-in retry logic (2 attempts, 10s delay)
3. Re-run the failed job from the GitHub Actions UI
4. Or create a patch release: `v1.2.1`

### If you need to skip a platform

Comment out the platform section in `release.yml`:

```yaml
# modrinth-id: screenshot-manager-enhanced
# modrinth-token: ${{ secrets.MODRINTH_TOKEN }}
```

### Common errors

| Error | Solution |
|-------|----------|
| `Invalid token` | Regenerate token and update GitHub secret |
| `Version already exists` | Bump version number in `gradle.properties` |
| `Project not found` | Verify project ID/slug in workflow |

---

## Adding a New Minecraft Version

Before creating a release that includes a new Minecraft version (e.g., `26.2`):

1. **Create Properties File**: Add `versionProperties/<mc_ver>.properties` defining dependencies (`fabric_version`, `loader_version`, `cloth_config_version`, `modmenu_version`, `owo_version`, `java_version`, and `ui_version`).
2. **Version Bridges**: If the new MC version has API changes, provide the appropriate version-specific implementations (under `common/src/client/java-<ui_version>/`).
3. **Verify Locally**:
   ```bash
   # Run tests for the version
   ./gradlew :common:test -Pmc_ver=<mc_ver> --no-daemon

   # Build fabric artifact
   ./gradlew :fabric:build -Pmc_ver=<mc_ver> --no-daemon

   # Verify all configured versions build together
   ./gradlew buildAllFabric --no-daemon
   ```

---

## FAQ

**Q: Do I need to manually update release workflows when adding a Minecraft version?**  
No. The release and build workflows automatically scan `versionProperties/*.properties` dynamically to discover all supported versions (versions `26.x` and `>= 1.21.11` without `build_disabled=true`).

**Q: Can I test without publishing?**  
Create a pre-release tag like `v1.0.0-rc1`. It will still publish, but marked as pre-release.

**Q: What if I forget to update the changelog?**  
The upload works but shows "No changelog provided" on platforms.

**Q: How do I disable a version from automated builds or releases?**  
Add `build_disabled=true` to the respective `versionProperties/<mc_ver>.properties` file.
