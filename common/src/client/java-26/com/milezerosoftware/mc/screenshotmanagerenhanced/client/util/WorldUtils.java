package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.util.StringSanitizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

public class WorldUtils {

    private static final Minecraft client = Minecraft.getInstance();

    /**
     * Gets a unique identifier for the current game world.
     *
     * <ul>
     * <li>For single-player worlds, this is the folder name of the world save.</li>
     * <li>For multiplayer servers, this is the server name, or the IP address if
     * the name is unavailable.</li>
     * <li>If the client is not in a world (e.g., on the main menu), it returns
     * "MENU".</li>
     * </ul>
     *
     * @return A {@link String} representing the world's unique identifier.
     */
    @NotNull
    public static String getWorldId() {
        if (client.level == null) {
            return "MENU";
        }

        if (client.isSingleplayer()) {
            System.out.println("Client is in singleplayer");
            IntegratedServer server = client.getSingleplayerServer();
            if (server != null) {
                try {
                    // Get the actual folder name by resolving the canonical path
                    String folderName = server.getWorldPath(LevelResource.ROOT).toFile().getCanonicalFile().getName();
                    System.out.println("Unique Level folder name: " + folderName);
                    return folderName;
                } catch (Exception e) {
                    System.out.println("Failed to resolve unique folder name: " + e.getMessage());
                    // Fallback to the level name if canonical path resolution fails
                    System.out.println("World Save Path ID: " + LevelResource.ROOT.id());
                    return server.getWorldData().getLevelName();
                }
            }
        } else if (!client.isSingleplayer()) {
            System.out.println("Client is in multiplayer");
            if (client.getCurrentServer() != null) {
                return client.getCurrentServer().name;
            }
            // Fallback to IP address if joined via Direct Connect
            if (client.getConnection() != null && client.getConnection().getConnection() != null) {
                InetSocketAddress address = (InetSocketAddress) client.getConnection().getConnection().getRemoteAddress();
                if (address != null) {
                    return address.getHostString();
                }
            }
        }

        return "UNKNOWN";
    }

    /**
     * Gets a filesystem-safe version of the world identifier.
     * <p>
     * This method sanitizes the output of {@link #getWorldId()} by replacing
     * characters
     * that are illegal in directory names on most operating systems.
     *
     * @return A sanitized {@link String} suitable for use as a directory name.
     * @see #getWorldId()
     */
    @NotNull
    public static String getSafeWorldId() {
        return sanitize(getWorldId());
    }

    /**
     * Sanitizes a string for use as a filesystem directory name.
     * 
     * @param input The string to sanitize.
     * @return A sanitized version of the string.
     */
    @NotNull
    public static String sanitize(String input) {
        return StringSanitizer.sanitizeForFilesystem(input);
    }

    /**
     * Gets the display name of the current world.
     *
     * @return A {@link String} representing the world's display name.
     */
    @NotNull
    public static String getWorldName() {
        if (client.level == null) {
            return "MENU";
        }

        if (client.isSingleplayer() && client.getSingleplayerServer() != null) {
            return client.getSingleplayerServer().getWorldData().getLevelName();
        }

        if (!client.isSingleplayer() && client.getCurrentServer() != null) {
            return client.getCurrentServer().name;
        }

        return "UNKNOWN";
    }

    /**
     * Gets the identifier of the current dimension.
     *
     * @return A {@link String} such as "minecraft:overworld", or "UNKNOWN".
     */
    @NotNull
    public static String getDimension() {
        if (client.level != null) {
            String dimension = client.level.dimension().identifier().toString();
            return dimension.replace("minecraft:", "").replace("minecraft_", "");
        }
        return "UNKNOWN";
    }

    /**
     * Calculates the number of in-game days played in the current world.
     *
     * @return The number of days as a {@code long}, or 0.
     */
    public static long getDaysPlayed() {
        if (client.level != null) {
            return client.level.getOverworldClockTime() / 24000L;
        }
        return 0;
    }

    /**
     * Gets the current game difficulty.
     *
     * @return A {@link String} (e.g., "Peaceful", "Hard"), or "UNKNOWN".
     */
    @NotNull
    public static String getDifficulty() {
        if (client.level != null) {
            String name = client.level.getDifficulty().getSerializedName();
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return "UNKNOWN";
    }

    /**
     * Gets the current Minecraft version.
     *
     * @return A {@link String} of the game version.
     */
    @NotNull
    public static String getVersion() {
        return client.getLaunchedVersion();
    }

    /**
     * Gets the current player's game mode.
     *
     * @return A {@link String} (e.g., "Survival", "Creative"), or "UNKNOWN".
     */
    @NotNull
    public static String getGameMode() {
        if (client.gameMode != null && client.gameMode.getPlayerMode() != null) {
            String name = client.gameMode.getPlayerMode().name().toLowerCase();
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return "UNKNOWN";
    }
}
