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
     * <li>For single-player worlds, this is the folder name of the world save.</li>
     * <li>For multiplayer servers, this is the server's IP address.</li>
     * <li>If the client is not in a world (e.g., on the main menu), it returns
     * "MENU".</li>
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
                // Returns the folder name of the world
                return server.getSavePath(WorldSavePath.ROOT).getFileName().toString();
            }
        } else if (client.getNetworkHandler() != null && client.getNetworkHandler().getConnection() != null) {
            InetSocketAddress address = (InetSocketAddress) client.getNetworkHandler().getConnection().getAddress();
            if (address != null) {
                return address.getHostString();
            }
        }

        return "UNKNOWN";
    }

    /**
     * Gets the display name of the current world.
     *
     * @return A {@link String} representing the world's display name.
     */
    @NotNull
    public static String getWorldName() {
        if (client.world == null) {
            return "MENU";
        }

        if (client.isInSingleplayer() && client.getServer() != null) {
            return client.getServer().getSaveProperties().getLevelName();
        }

        if (!client.isInSingleplayer() && client.getCurrentServerEntry() != null) {
            return client.getCurrentServerEntry().name;
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
        if (client.world != null) {
            return client.world.getRegistryKey().getValue().toString();
        }
        return "UNKNOWN";
    }

    /**
     * Calculates the number of in-game days played in the current world.
     *
     * @return The number of days as a {@code long}, or 0.
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
     * @return A {@link String} (e.g., "Peaceful", "Hard"), or "UNKNOWN".
     */
    @NotNull
    public static String getDifficulty() {
        if (client.world != null) {
            String name = client.world.getDifficulty().getName();
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
        return client.getGameVersion();
    }

    /**
     * Gets the current player's game mode.
     *
     * @return A {@link String} (e.g., "Survival", "Creative"), or "UNKNOWN".
     */
    @NotNull
    public static String getGameMode() {
        if (client.interactionManager != null && client.interactionManager.getCurrentGameMode() != null) {
            String name = client.interactionManager.getCurrentGameMode().name().toLowerCase();
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return "UNKNOWN";
    }
}
