package com.milezerosoftware.mc.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

public class WorldUtils {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    /**
     * Gets a unique identifier for the current game world.
     *
     * <ul>
     *     <li>For single-player worlds, this is the folder name of the world save.</li>
     *     <li>For multiplayer servers, this is the server's IP address.</li>
     *     <li>If the client is not in a world (e.g., on the main menu), it returns "MENU".</li>
     * </ul>
     *
     * @return A {@link String} representing the world's unique identifier.
     */
    @NotNull
    public static String getWorldId() {
        if (client.world == null) {
            return "MENU";
        }

        if (client.isInSingleplayer()) {
            IntegratedServer server = client.getServer();
            if (server != null) {
                return server.getSavePath(WorldSavePath.ROOT).getParent().getFileName().toString();
            }
        } else {
            InetSocketAddress address = (InetSocketAddress) client.getNetworkHandler().getConnection().getAddress();
            if (address != null) {
                return address.getHostString();
            }
        }

        // Fallback for edge cases where server/address info is not available
        return "UNKNOWN";
    }

    /**
     * Gets the display name of the current world.
     *
     * <ul>
     *     <li>For single-player worlds, it's typically the same as the world ID.</li>
     *     <li>For multiplayer servers, this could be the MOTD or a custom name.</li>
     *     <li>If not in a world, returns "MENU".</li>
     * </ul>
     *
     * @return A {@link String} representing the world's display name.
     */
    @NotNull
    public static String getWorldName() {
        if (client.world == null) {
            return "MENU";
        }

        // For multiplayer, the server name from the multiplayer screen is a good option.
        if (!client.isInSingleplayer() && client.getCurrentServerEntry() != null) {
            return client.getCurrentServerEntry().name;
        }

        // For single-player, the level name is the most reliable.
        if (client.isInSingleplayer() && client.getServer() != null) {
            return client.getServer().getSavePath(WorldSavePath.ROOT).getParent().getFileName().toString();
        }

        // A final fallback if other methods fail.
        return "UNKNOWN";
    }

    /**
     * Gets the identifier of the current dimension.
     *
     * @return A {@link String} such as "minecraft:overworld", or "UNKNOWN" if not in a world.
     */
    @NotNull
    public static String getDimension() {
        if (client.world != null) {
            return client.world.getDimension().effects().toString();
        }
        return "UNKNOWN";
    }

    /**
     * Calculates the number of in-game days played in the current world.
     *
     * @return The number of days as a {@code long}, or 0 if not in a world.
     */
    public static long getDaysPlayed() {
        if (client.world != null) {
            return client.world.getTimeOfDay() / 24000L;
        }
        return 0;
    }

    /**
     * Gets the current game difficulty.
     *
     * @return A {@link String} representing the difficulty (e.g., "Peaceful", "Hard"), or "UNKNOWN".
     */
    @NotNull
    public static String getDifficulty() {
        if (client.world != null) {
            return client.world.getDifficulty().getName();
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
        return MinecraftClient.getInstance().getGameVersion();
    }

    /**
     * Gets the current player's game mode.
     *
     * @return A {@link String} (e.g., "Survival", "Creative"), or "UNKNOWN" if not available.
     */
    @NotNull
    public static String getGameMode() {
        if (client.interactionManager != null) {
            return client.interactionManager.getCurrentGameMode().name();
        }
        return "UNKNOWN";
    }
}
