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

# Change summary: Introduces configuration persistence via `ConfigManager` and adds data models for `GroupingMode` and `WorldConfig` to support future features.

## File: src/main/java/com/milezerosoftware/mc/config/ConfigManager.java
### L28: [MEDIUM] Lazy initialization is not thread-safe.
`getScreenshotFilename` (and thus `getInstance`) may be called from worker threads during async screenshot saving. This creates a race condition for `instance`.

Suggested change:
```java
    /**
     * Gets the current configuration instance.
     * If not loaded, it attempts to load from disk.
     *
     * @return The active ModConfig.
     */
    public static synchronized ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }
```

### L53: [MEDIUM] Uncaught `JsonSyntaxException` can crash the game.
`GSON.fromJson` throws a `JsonSyntaxException` (a `RuntimeException`) if the JSON is malformed. This block only catches `IOException`, so a typo in the config file will crash the client.

Suggested change:
```java
            try {
                String json = Files.readString(configFile);
                instance = GSON.fromJson(json, ModConfig.class);
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                // If loading fails, fallback to default (logging would be good here)
                System.err.println("Failed to load Screenshot Manager config: " + e.getMessage());
                instance = new ModConfig();
            }
```

### L56: [LOW] Use of `System.err.println`.
Direct usage of `System.err` is discouraged in Fabric mods. Consider using a `Logger` (e.g., `org.slf4j.LoggerFactory.getLogger("screenshot-manager")`) for proper log level control and formatting.

## File: src/main/java/com/milezerosoftware/mc/config/ModConfig.java
### L12: [LOW] Verbose fully qualified names.
While valid, using fully qualified names for `Map` and `HashMap` is verbose. Consider importing them for cleaner code.

Suggested change:
```java
import java.util.HashMap;
import java.util.Map;

public class ModConfig {
    // ...
    
    // Per-World Rules: Key = WorldName/IP, Value = Configuration
    public Map<String, WorldConfig> worldRules = new HashMap<>();
    
    // ...
}
```