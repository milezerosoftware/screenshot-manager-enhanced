title:	feat: Global and Per-World Rules Configuration System (GSON)
state:	OPEN
author:	scriptmunkeeofficial
labels:	Feature
comments:	0
assignees:	scriptmunkeeofficial
projects:	
milestone:	
number:	4
--
## 🎯 Objective

- Create `ModConfig class`
- Implement Save/Load logic using `FabricLoader.getInstance().getConfigDir()`.

---

## 📝 Background

The ability to store and read configuration options/settings is a major need

---

## 🧑‍💻 User Personas

- **Player**: 

---

## 📖 User Stories

(List the user stories that define the feature's requirements. Follow the "As a [user], I want to [action], so that [benefit]" format.)

- As a user, I want to...
- As a user, I want to...

---

## 🎨 Design & UX

Fabric includes GSON, a powerful library for reading/writing JSON.

1. **GlobalConfig**: Store boolean flags (e.g., `enableMetadata`, `groupingMode`).
2. **WorldRules**: A `Map<String, WorldSettings>` where the string is the World Name/IP.
3. **Storage**: Save to `config/screenshotmanager.json`

Implementation Note: the GlobalConfig and WorldRules will be in the identified sigural file. The GSON file will need to be constructed to support both configuration seettings.

---

## 📈 Justification & Benefits

- **User Benefit:** When a game is loaded, the configurations options at a global and per-world are made available to game for usage.
- **User Benefits:** The play can update the global or per-world configuration options

---

## ✅ Definition of Done

(What must be completed for this feature to be considered "shipped"?)

- [ ] All user stories are implemented and meet acceptance criteria.
- [ ] Designs are implemented to spec.
- [ ] All new code is covered by tests.
- [ ] All existing tests pass.
- [ ] The feature is documented (if applicable).
- [ ] The code is reviewed by at least one other developer.

---

## 🧪 Testing Plan

(Describe the testing strategy for this feature.)

- **Unit Tests:**
- **Integration Tests:**
- **End-to-End (E2E) Tests:**
- **Manual Testing:**

---

## ⚠️ Risks & Mitigation

(List any potential risks, dependencies, or open questions.)

- **Risk:**
- **Mitigation:**

---

## 🖼️ Assets & Artifacts

(List any design files, documentation, or other assets related to this feature.)

- [Link to Figma Mockups]()
- [Link to Technical Spec]()

---

## 🚫 Out of Scope

(What is explicitly not being addressed in this feature? This helps to manage scope and expectations.)

-
-

---

## 🔍 Code Review Findings

# Change summary: Implements configuration persistence using Gson and updates the client-side integration to support the new configuration structure.

## File: src/main/java/com/milezerosoftware/mc/config/ConfigManager.java
### L58: [HIGH] Potential StackOverflowError and Data Loss on load failures.
Two issues here:
1. If the configuration file is empty (0 bytes), `GSON.fromJson` returns `null`. This leaves `instance` as `null`, causing `getInstance()` (L34) to call `load()` repeatedly in an infinite recursion loop until the game crashes.
2. If `GSON.fromJson` throws a `JsonSyntaxException` (malformed JSON), the catch block resets `instance` to a default `ModConfig`. If the user then saves the config (e.g., via ModMenu), the original malformed file—and the user's data—is silently overwritten and lost.

Suggested change:
```java
            try {
                String json = Files.readString(configFile);
                instance = GSON.fromJson(json, ModConfig.class);
                // Fix for infinite recursion on empty file
                if (instance == null) {
                    LOGGER.warn("Configuration file was empty. Resetting to defaults.");
                    instance = new ModConfig();
                    save(configFile);
                }
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.error("Failed to load Screenshot Manager config: {}", e.getMessage());
                // Backup broken file to prevent data loss
                try {
                    Files.copy(configFile, configFile.resolveSibling(CONFIG_FILE_NAME + ".broken"));
                    LOGGER.info("Backed up broken config to {}.broken", CONFIG_FILE_NAME);
                } catch (IOException copyEx) {
                    LOGGER.error("Failed to backup broken config", copyEx);
                }
                instance = new ModConfig();
            }
```

### L46: [MEDIUM] Thread safety inconsistency.
`getInstance` is `synchronized`, but `load` and `save` are not. If `load` or `save` are called from other threads (e.g., a command reload or auto-save) while `getInstance` is running, race conditions could occur. Since `getInstance` locks on the class, `load` and `save` should likely be synchronized as well to ensure atomic reads/writes of `instance` and file operations.

Suggested change:
```java
    /**
     * Loads the configuration from disk.
     */
    public static synchronized void load() {
        load(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME));
    }

    // ...

    public static synchronized void save() {
        // ...
    }
```

## File: src/client/java/com/milezerosoftware/mc/client/compat/ModMenuIntegration.java
### L23: [LOW] Reduced readability due to Fully Qualified Names.
The change removes the `ModConfig` import and uses verbose fully qualified names (FQNs) for `ConfigManager`. This makes the code harder to read and maintain.

Suggested change:
```java
import com.milezerosoftware.mc.config.ConfigManager;

// ... inside getModConfigScreenFactory ...

                        builder.getOrCreateCategory(Text.literal("General"))
                                        .addEntry(builder.entryBuilder()
                                                        .startStrField(Text.literal("Storage Folder"),
                                                                        ConfigManager.getInstance().customPath)
                                                        .setDefaultValue("screenshots")
                                                        .setSaveConsumer(newValue -> ConfigManager.getInstance().customPath = newValue)
                                                        .build());
```