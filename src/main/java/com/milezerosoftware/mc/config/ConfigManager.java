package com.milezerosoftware.mc.config;

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
 */
public class ConfigManager {

    private static final String CONFIG_FILE_NAME = "screenshotmanager.json";
    private static ModConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("screenshot-manager");

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
        if (instance == null) {
            load();
        }
        return instance;
    }

    /**
     * Loads the configuration from disk.
     * If the file does not exist, a new specific default configuration is created
     * and saved.
     */
    public static void load() {
        load(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME));
    }

    /**
     * Internal load method for testing or specific paths.
     * 
     * @param configFile The full path to the configuration file.
     */
    public static void load(Path configFile) {
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                instance = GSON.fromJson(json, ModConfig.class);
            } catch (IOException | JsonSyntaxException e) {
                // If loading fails, fallback to default
                LOGGER.error("Failed to load Screenshot Manager config: {}", e.getMessage());
                instance = new ModConfig();
            }
        } else {
            instance = new ModConfig();
            save(configFile);
        }
    }

    /**
     * Saves the current configuration to disk.
     */
    public static void save() {
        save(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME));
    }

    /**
     * Internal save method for testing or specific paths.
     * 
     * @param configFile The full path to the configuration file.
     */
    public static void save(Path configFile) {
        if (instance == null)
            return;

        try {
            String json = GSON.toJson(instance);
            Files.writeString(configFile, json);
        } catch (IOException e) {
            LOGGER.error("Failed to save Screenshot Manager config: {}", e.getMessage());
        }
    }
}
