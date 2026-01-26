package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.SharedConstants;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;

/**
 * Collects metadata from the current Minecraft game state for embedding into
 * screenshots.
 * 
 * <p>
 * This collector gathers information about the current world, player position,
 * biome, dimension, and other game state details. All data is collected on the
 * Render thread to ensure access to game state objects.
 * </p>
 * 
 * <p>
 * The collected metadata is formatted for human readability:
 * </p>
 * <ul>
 * <li>Dimensions and biomes use Pascal Case (e.g., "The Nether", "Birch
 * Forest")</li>
 * <li>Coordinates use labeled format (e.g., "x: 70, y: 87, z: 159")</li>
 * </ul>
 */
public class ScreenshotMetadataCollector {

    /**
     * Collects metadata from the current game state.
     * 
     * <p>
     * This method must be called from the Render thread to ensure
     * access to client-side game objects (player, world, etc.).
     * </p>
     * 
     * <p>
     * Handles edge cases gracefully, providing "Unknown" fallbacks
     * when game state is unavailable (e.g., screenshot from main menu).
     * </p>
     *
     * @return A populated {@link MetadataHandler.ScreenshotMetadata} instance
     */
    public static MetadataHandler.ScreenshotMetadata collect() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        // Fallbacks for edge cases (e.g., screenshot from menu)
        String worldName = WorldUtils.getWorldId();

        // Dimension - formatted as Pascal Case
        String dimension = world != null
                ? toPascalCase(world.getRegistryKey().getValue().getPath())
                : "Unknown";

        // Coordinates - formatted as "x: 12, y: 80, z: 30"
        String coordinates = formatCoordinates(player);

        // In-Game Days
        double days = world != null ? world.getTimeOfDay() / 24000.0 : 0;
        String daysPlayed = String.format("%.2f d", days);

        // Real-Time World Age (player's total play time in this world)
        int totalTicks = 0;
        if (player != null) {
            try {
                totalTicks = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
            } catch (Exception ignored) {
            }
        }

        double realDays = totalTicks / 20.0 / 60.0 / 60.0 / 24.0;
        String worldAge = String.format("%.2f d", realDays);

        String playerName = player != null
                ? player.getName().getString()
                : "Unknown";
        String difficulty = world != null
                ? world.getDifficulty().getTranslatableName().getString()
                : "Unknown";
        String gameMode = client.interactionManager != null
                ? client.interactionManager.getCurrentGameMode().asString()
                : "Unknown";
        String minecraftVersion = getMinecraftVersionName();

        // Biome - formatted as Pascal Case
        String biome = "Unknown";
        if (world != null && player != null) {
            try {
                String rawBiome = world.getBiome(player.getBlockPos()).getKey().get().getValue().getPath();
                biome = toPascalCase(rawBiome);
            } catch (Exception ignored) {
            }
        }

        return new MetadataHandler.ScreenshotMetadata(
                worldName,
                dimension,
                coordinates,
                daysPlayed,
                playerName,
                difficulty,
                gameMode,
                minecraftVersion,
                biome,
                worldAge);
    }

    /**
     * Formats player coordinates as a labeled string.
     *
     * @param player The player entity, or null if unavailable
     * @return Formatted coordinates string (e.g., "x: 70, y: 87, z: 159")
     */
    private static String formatCoordinates(ClientPlayerEntity player) {
        if (player == null) {
            return "x: 0, y: 0, z: 0";
        }
        BlockPos pos = player.getBlockPos();
        return String.format("x: %d, y: %d, z: %d", pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Converts snake_case or lowercase identifiers to Pascal Case with spaces.
     * 
     * <p>
     * Examples:
     * </p>
     * <ul>
     * <li>"the_nether" → "The Nether"</li>
     * <li>"plains" → "Plains"</li>
     * <li>"birch_forest" → "Birch Forest"</li>
     * </ul>
     *
     * @param input The input string in snake_case or lowercase
     * @return The formatted Pascal Case string
     */
    private static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] parts = input.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                if (i > 0) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    result.append(parts[i].substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    /**
     * Gets the Minecraft version name using reflection for cross-version
     * compatibility.
     * 
     * <p>
     * The GameVersion interface changed between Minecraft versions:
     * </p>
     * <ul>
     * <li>1.21.8+: Uses {@code name()} method</li>
     * <li>Earlier versions: Uses {@code getName()} method</li>
     * </ul>
     * 
     * <p>
     * This method tries both approaches via reflection to maintain
     * compatibility across multiple Minecraft versions.
     * </p>
     *
     * @return The Minecraft version string (e.g., "1.21.8"), or "Unknown" if
     *         retrieval fails
     */
    private static String getMinecraftVersionName() {
        try {
            Object gameVersion = SharedConstants.getGameVersion();

            // Try name() first (1.21.8+)
            try {
                java.lang.reflect.Method nameMethod = gameVersion.getClass().getMethod("name");
                return (String) nameMethod.invoke(gameVersion);
            } catch (NoSuchMethodException ignored) {
                // Method doesn't exist, try getName() for older versions
            }

            // Fall back to getName() (older versions)
            try {
                java.lang.reflect.Method getNameMethod = gameVersion.getClass().getMethod("getName");
                return (String) getNameMethod.invoke(gameVersion);
            } catch (NoSuchMethodException ignored) {
                // Neither method exists
            }

            // Last resort: use toString()
            return gameVersion.toString();

        } catch (Exception e) {
            return "Unknown";
        }
    }
}
