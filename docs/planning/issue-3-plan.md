# Issue Resolution Plan: feat: Implement World Identification Utility

**Issue Link**: https://github.com/milezerosoftware/screenshot-manager/issues/3
**Proposed Branch**: `git checkout -b issue-3`

## Issue Summary
The goal is to implement a client-side utility class, `WorldUtils`, that provides detailed information about the current game session. This includes determining a unique "World Key" (Folder Name for Singleplayer, IP for Multiplayer) and retrieving various world states like dimension, days played, difficulty, version, and game mode.

## Affected Codebase Areas
-   **Client**: This utility relies heavily on `net.minecraft.client.MinecraftClient`, which is strictly client-side.
-   **New File**: `src/client/java/com/milezerosoftware/mc/client/util/WorldUtils.java`

## Implementation Plan

### Prerequisites
- Ensure you are on the `main` branch and it's up-to-date.
- Create a new branch: `git checkout -b issue-3`

### Step-by-Step Implementation

1.  **Step 1: Create `WorldUtils` Class Structure**
    -   **Files to modify**: Create `src/client/java/com/milezerosoftware/mc/client/util/WorldUtils.java`.
    -   **Changes needed**: Define the class and get the `MinecraftClient` instance.
    -   **Verification**: Ensure the file exists and compiles.

2.  **Step 2: Implement World Identification Methods**
    -   **Files to modify**: `src/client/java/com/milezerosoftware/mc/client/util/WorldUtils.java`.
    -   **Changes needed**:
        -   `getWorldId()`: Return Level Name (SP) or Server IP (MP).
        -   `getWorldName()`: Return the display name of the world.
        -   **Null Safety**: Ensure these return a safe default (e.g., "MENU") if not currently in a world.

3.  **Step 3: Implement Game State Methods**
    -   **Files to modify**: `src/client/java/com/milezerosoftware/mc/client/util/WorldUtils.java`.
    -   **Changes needed**:
        -   `getDimension()`: Return current dimension ID (e.g., `minecraft:overworld`).
        -   `getDaysPlayed()`: Calculate days from world time.
        -   `getDifficulty()`: Return current difficulty (Peaceful, Easy, etc.).
        -   `getVersion()`: Return Minecraft version.
        -   `getGameMode()`: Return current game mode (Survival, Creative, etc.).
    -   **Verification**: Methods should compile and handle potential null references (e.g., `client.world`).

## Todo Checklist
- [ ] Create branch `issue-3`.
- [ ] Create package `com.milezerosoftware.mc.client.util`.
- [ ] Create `WorldUtils.java`.
- [ ] Implement `getWorldId()` (SP: Folder/Level Name, MP: Server IP).
- [ ] Implement `getWorldName()`.
- [ ] Implement `getDimension()`.
- [ ] Implement `getDaysPlayed()`.
- [ ] Implement `getDifficulty()`.
- [ ] Implement `getVersion()`.
- [ ] Implement `getGameMode()`.
- [ ] Build project (`./gradlew build`) to ensure no compilation errors.
- [ ] Commit changes.

## Testing Strategy
-   **Manual Verification**:
    -   As this logic relies on active game state (being connected to a server/world), unit tests are complex to set up.
    -   **Recommendation**: Temporarily inject a logging statement into `ScreenshotManagerClient.onInitializeClient` or a mixin that prints `WorldUtils` data when joining a world to verify output in the console.
-   **Automated Build**: Run `./gradlew build` to ensure the code uses valid mappings and types.

## Potential Risks & Considerations
-   **Null Safety**: `MinecraftClient.getInstance().world` or `.server` can be null (e.g., main menu). All methods **MUST** handle these cases gracefully to prevent crashes.
-   **Privacy**: Be aware that `Server IP` is PII. Ensure it's not logged or exposed unintentionally.
-   **Mappings**: Ensure we use the correct Fabric/Yarn mappings for 1.21 (e.g., `client.world`, `client.getNetworkHandler()`).
