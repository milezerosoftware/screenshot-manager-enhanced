package com.milezerosoftware.mc.screenshotmanagerenhanced.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the persistence of the {@link ModConfig}.
 * <p>
 * Configuration is stored in <code>config/screenshotmanager.json</code>.
 * Example structure:
 * 
 * <pre>
 * {
 *   "enableMetadata": true,
 *   "groupingMode": "WORLD",
 *   "worldRules": {
 *     "My Multiplayer Server": {
 *       "customPath": "",
 *       "active": true
 *     },
 *     "Singleplayer World": {
 *       "active": false
 *     }
 *   }
 * }
 * </pre>
 */
public class ConfigManager {

    private static final String CONFIG_FILE_NAME = "screenshot-manager-enhanced.json";
    private static ModConfig instance;
    private static long lastModified = -1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("screenshot-manager-enhanced");

    private ConfigManager() {
        // Private constructor to enforce Singleton usage
    }

    /**
     * Gets the current configuration instance.
     * If not loaded, it attempts to load from disk.
     *
     * @return The active ModConfig.
     */
    public static synchronized ModConfig getInstance() {
        if (instance == null || hasConfigChangedOnDisk()) {
            load();
        }
        return instance;
    }

    private static Path getDefaultConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    private static boolean hasConfigChangedOnDisk() {
        try {
            Path path = getDefaultConfigPath();
            if (Files.exists(path)) {
                long currentModified = Files.getLastModifiedTime(path).toMillis();
                return currentModified > lastModified;
            }
        } catch (Exception e) {
            // If we can't check (e.g. FabricLoader not available in tests), assume no
            // change
        }
        return false;
    }

    /**
     * Loads the configuration from disk.
     * If the file does not exist, a new specific default configuration is created
     * and saved.
     */
    public static synchronized void load() {
        load(getDefaultConfigPath());
    }

    /**
     * Internal load method for testing or specific paths.
     * 
     * @param configFile The full path to the configuration file.
     */
    public static synchronized void load(Path configFile) {
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                instance = GSON.fromJson(json, ModConfig.class);
                lastModified = Files.getLastModifiedTime(configFile).toMillis();

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
                    Files.copy(configFile, configFile.resolveSibling(configFile.getFileName().toString() + ".broken"));
                    LOGGER.info("Backed up broken config to {}.broken", configFile.getFileName());
                } catch (IOException copyEx) {
                    LOGGER.error("Failed to backup broken config", copyEx);
                }
                instance = new ModConfig();
                lastModified = System.currentTimeMillis(); // Prevent constant reloading of broken file
            }
        } else {
            instance = new ModConfig();
            save(configFile);
            try {
                lastModified = Files.getLastModifiedTime(configFile).toMillis();
            } catch (IOException e) {
                lastModified = System.currentTimeMillis();
            }
        }
    }

    /**
     * Saves the current configuration to disk.
     */
    public static synchronized void save() {
        save(getDefaultConfigPath());
    }

    /**
     * Internal save method for testing or specific paths.
     * 
     * @param configFile The full path to the configuration file.
     */
    public static synchronized void save(Path configFile) {
        if (instance == null)
            return;

        try {
            String json = GSON.toJson(instance);
            Files.writeString(configFile, json);
            lastModified = Files.getLastModifiedTime(configFile).toMillis();
        } catch (IOException e) {
            LOGGER.error("Failed to save Screenshot Manager config: {}", e.getMessage());
        }
    }
}
