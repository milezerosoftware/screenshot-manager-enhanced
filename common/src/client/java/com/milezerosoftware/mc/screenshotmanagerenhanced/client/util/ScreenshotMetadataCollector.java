package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.SharedConstants;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;

public class ScreenshotMetadataCollector {

    public static MetadataHandler.ScreenshotMetadata collect() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        // Fallbacks for edge cases (e.g., screenshot from menu)
        String worldName = WorldUtils.getWorldId(); // Already handles null

        // Dimension - formatted as Pascal Case (e.g., "The Nether" instead of
        // "the_nether")
        String dimension = world != null
                ? toPascalCase(world.getRegistryKey().getValue().getPath())
                : "Unknown";

        // Coordinates - formatted as "x: 12, y: 80, z: 30"
        String coordinates = formatCoordinates(player);

        // In-Game Days
        double days = world != null ? world.getTimeOfDay() / 24000.0 : 0;
        String daysPlayed = String.format("%.2f d", days);

        // Real-Time World Age (Time with World Open)
        // Note: 1 tick = 0.05 seconds. 1 day = 1728000 ticks.
        // We use Math.max to avoid potential issues if stat is missing/0
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

        // Biome - formatted as Pascal Case (e.g., "Plains" instead of "plains")
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
     * Formats player coordinates as "x: 12, y: 80, z: 30"
     */
    private static String formatCoordinates(ClientPlayerEntity player) {
        if (player == null) {
            return "x: 0, y: 0, z: 0";
        }
        BlockPos pos = player.getBlockPos();
        return String.format("x: %d, y: %d, z: %d", pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Converts snake_case or lowercase to Pascal Case.
     * e.g., "the_nether" -> "The Nether", "plains" -> "Plains"
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
     * The GameVersion interface changed between Minecraft versions:
     * - 1.21.8+: Uses name() method
     * - Earlier versions (1.21.5 and below): Uses getName() method
     * 
     * @return The Minecraft version string (e.g., "1.21.8"), or "Unknown" if
     *         retrieval fails.
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
            System.err.println("[Screenshot Manager Enhanced] Failed to get Minecraft version: " + e.getMessage());
            return "Unknown";
        }
    }
}
