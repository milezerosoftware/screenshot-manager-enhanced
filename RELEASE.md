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

Dependencies are defined on a per-Minecraft-version basis in `versionProperties/<mc_ver>.properties`:

```properties
modrinth_slug=screenshot-manager-enhanced
modrinth_id=xs5bRkXn
modrinth_game_versions=26.2
modrinth_mod_loaders=fabric
modrinth_required_dependencies=modmenu, cloth-config
```

---

## Configuration & Environment Variables (`.env`)

The release manager supports loading credentials and flags from a local `.env` file in the project root. The file is excluded from version control via `.gitignore`.

### Available `.env` Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MODRINTH_TOKEN` | Production Modrinth Personal Access Token (with `VERSION_CREATE` scope) | *None* |
| `MODRINTH_STAGING_TOKEN` | Token for Modrinth Staging Sandbox (`https://staging-api.modrinth.com`) | Falls back to `MODRINTH_TOKEN` |
| `MODRINTH_STAGING` | Route Modrinth uploads to staging sandbox (`true` / `false`) | `false` |
| `MODRINTH_DRAFT` | Upload version with `status: "draft"` instead of `status: "listed"` | `false` |

### Setting Your Token
You can provide your token in one of three ways:
1. **Local `.env` file (recommended)**:
   ```properties
   MODRINTH_TOKEN=mrp_xxxxxxxxxxxxxxxxxxxx
   ```
2. **Environment variable**:
   ```bash
   export MODRINTH_TOKEN="mrp_xxxxxxxxxxxxxxxxxxxx"
   ```
3. **Gradle property**:
   ```bash
   ./gradlew publishRelease -PmodrinthToken="mrp_xxxxxxxxxxxxxxxxxxxx"
   ```

*Note: The task validates authentication against the Modrinth API (`GET /v2/user`) up front before compiling artifacts or creating tags.*

---

## Sandbox & Test Modes

The release workflow provides multiple levels of safe testing:

### 1. Dry-Run Mode (`-PdryRun=true`)
Simulates the entire workflow without making git commits, tags, pushes, GitHub releases, or Modrinth uploads:
```bash
./gradlew publishRelease -PdryRun=true
```

### 2. Modrinth Staging Sandbox (`-PmodrinthStaging=true`)
Directs API uploads to the Modrinth Staging API (`https://staging-api.modrinth.com`):
```bash
./gradlew publishRelease -PmodrinthStaging=true
```

### 3. Modrinth Draft Testing Mode (`-PmodrinthDraft=true`)
Uploads version artifacts with `status: "draft"` instead of `status: "listed"`. The version appears in your Modrinth project dashboard for verification and manual inspection without being publicly listed to players:
```bash
./gradlew publishRelease -PmodrinthDraft=true
```

---

## Troubleshooting & Resumability

### Resumable Workflow
If a release fails midway (for instance, network timeout or API error during Modrinth upload after creating the Git tag and GitHub draft release):
1. Fix the underlying issue (e.g. update token in `.env`).
2. Re-run `./gradlew publishRelease`.
3. The release manager detects the existing Git tag and GitHub draft release, skips duplicate creation, and smoothly resumes uploading to Modrinth and publishing the release!

### Common Errors

| Error | Solution |
|-------|----------|
| `Modrinth token authentication failed` | Check `MODRINTH_TOKEN` in `.env`. Ensure token has `VERSION_CREATE` scope. |
| `Invalid character '-' in base62 encoding` | Ensure `modrinth_id` in `versionProperties/` uses the Base62 project ID (`xs5bRkXn`), not the hyphenated slug. |
| `Tag already exists locally` | If the release is already published on GitHub, bump the version via `prepareRelease`. If it is a draft release, `publishRelease` will automatically resume. |
| `Working tree is dirty` | Commit or stash any uncommitted changes before releasing. |

---

## Adding a New Minecraft Version

When a new Minecraft version is released, follow this structured process before creating a release:

### 1. Dependency Readiness Audit (Pre-flight Gate)

Before writing any code or modifying configuration, verify that **all five core dependencies** have officially published releases supporting the target Minecraft version:

| Dependency | Repository / Provider | Verification Check |
|---|---|---|
| **Fabric Loader** | [Fabric Meta](https://meta.fabricmc.net/) | Check `https://meta.fabricmc.net/v2/versions/loader/<mc_ver>` |
| **Fabric API** | [Fabric Maven](https://maven.fabricmc.net/) | Check `maven-metadata.xml` for `0.x.x+<mc_ver>` |
| **Cloth Config** | [Shedaniel Maven](https://maven.shedaniel.me/) | Check `cloth-config-fabric` for `<mc_ver>.x` build |
| **ModMenu** | [TerraformersMC Maven](https://maven.terraformersmc.com/) | Check `modmenu` releases for `<mc_ver>` |
| **owo-lib** | [Wisp Forest Maven](https://maven.wispforest.io/) | Check `owo-lib` for an artifact compiled against `<mc_ver>` |

> [!IMPORTANT]
> **Dependency Gate:** If *any* required dependency (particularly `owo-lib`) has not yet published an official release specifically supporting the target Minecraft version, **do not proceed with the upgrade**. Keep the upgrade branch in draft/on-hold until all supporting libraries are available.

---

### 2. Properties & Bridge Configuration

1. **Create Properties File:** Add `versionProperties/<mc_ver>.properties` declaring the exact dependencies and metadata.
2. **Bridge Reusability Policy:**
   - **Do not create redundant bridge files.** If the vanilla Minecraft APIs and library contracts haven't changed, reuse an existing bridge by pointing `ui_version` to the existing directory (e.g., `ui_version=26-2` or `ui_version=legacy`).
   - If minor vanilla API refactors occur (e.g. method moves), prefer **adaptive runtime resolution** in existing bridge helpers (`ScreenUtils`) to preserve cross-version compatibility without duplicating source trees.

---

### 3. Local Verification & Multi-Target Build

Run automated tests and verify that both the new version and all existing active versions compile cleanly:

```bash
# 1. Run unit tests for the new target version
./gradlew :common:test -Pmc_ver=<mc_ver> --no-daemon

# 2. Build the Fabric artifact for the new version
./gradlew :fabric:build -Pmc_ver=<mc_ver> --no-daemon

# 3. Verify all configured versions build cleanly together
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
