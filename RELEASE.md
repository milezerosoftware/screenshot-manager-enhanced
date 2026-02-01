# Release Guide

## TL;DR Quick Reference

```
┌─────────────────────────────────────────────────────────┐
│                    RELEASE WORKFLOW                      │
├─────────────────────────────────────────────────────────┤
│  1. Update CHANGELOG.md with release notes              │
│  2. Update mod_version in gradle.properties             │
│  3. git add . && git commit -m "chore: release vX.Y.Z"  │
│  4. git tag vX.Y.Z                                      │
│  5. git push origin main && git push origin vX.Y.Z      │
│  6. ☕ Wait ~5-10 min for builds                         │
│  7. Verify uploads on Modrinth + CurseForge             │
│  8. Publish GitHub release draft (optional)             │
└─────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Release Instructions

### Step 1: Update CHANGELOG.md

Add a new section for your release following the [Keep a Changelog](https://keepachangelog.com/) format:

```markdown
## [X.Y.Z] - YYYY-MM-DD

### Added
- New feature description

### Changed
- Changed behavior description

### Fixed
- Bug fix description
```

### Step 2: Update Version

Edit `gradle.properties`:

```properties
mod_version=X.Y.Z
```

### Step 3: Commit and Tag

```bash
# Stage changes
git add gradle.properties CHANGELOG.md

# Commit with release message
git commit -m "chore: release vX.Y.Z"

# Create annotated tag
git tag vX.Y.Z

# Push everything
git push origin main
git push origin vX.Y.Z
```

### Step 4: Wait for Automation

The GitHub Action will automatically:

1. ✅ Build all supported Minecraft versions (7+ JARs)
2. ✅ Upload each JAR to **Modrinth**
3. ✅ Upload each JAR to **CurseForge** *(when configured)*
4. ✅ Create a **GitHub Release** (draft)
5. ✅ Attach your changelog to all platforms

**Estimated time**: 5-10 minutes

### Step 5: Verify

Check uploads on:

- [GitHub Actions](https://github.com/milezerosoftware/screenshot-manager-enhanced/actions) — workflow status
- [Modrinth Versions](https://modrinth.com/mod/screenshot-manager-enhanced/versions) — new versions appear
- CurseForge Files page *(when configured)*

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

## FAQ

**Q: Do I need to do anything per Minecraft version?**  
No, the workflow handles all versions automatically from a single tag.

**Q: Can I test without publishing?**  
Create a pre-release tag like `v1.0.0-rc1`. It will still publish, but marked as pre-release.

**Q: What if I forget to update the changelog?**  
The upload works but shows "No changelog provided" on platforms.

**Q: How do I add more Minecraft versions?**  
Add to the `matrix.mc_ver` array in `release.yml`.
