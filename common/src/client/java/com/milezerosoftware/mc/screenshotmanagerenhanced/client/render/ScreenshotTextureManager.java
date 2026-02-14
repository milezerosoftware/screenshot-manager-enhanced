package com.milezerosoftware.mc.screenshotmanagerenhanced.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ScreenshotTextureManager {
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private static final Identifier LOADING_ID = Identifier.of("minecraft", "textures/item/clock_00.png"); // Temporary
                                                                                                           // placeholder

    public static Identifier getOrCreateThumbnail(Path path, int targetWidth) {
        String key = path.toString() + "_" + targetWidth;
        if (CACHE.containsKey(key))
            return CACHE.get(key);

        // Generate a unique ID for this request
        Identifier id = Identifier.of("screenshot-manager-enhanced",
                "thumb/" + targetWidth + "_" + System.currentTimeMillis() + "_"
                        + path.getFileName().toString().toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));

        // Register placeholder initially (using a known safe transparent or loading
        // texture would be better,
        // but for now we register a temp generic one or just reuse the ID with a dummy
        // texture)
        // Actually, we must register *something* so the renderer doesn't crash on
        // "missing texture"
        // Let's create a 1x1 transparent native image
        NativeImage placeholder = new NativeImage(1, 1, true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                new net.minecraft.client.texture.NativeImageBackedTexture(placeholder));

        CACHE.put(key, id);

        // Load real image asynchronously
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try (InputStream is = Files.newInputStream(path)) {
                NativeImage fullImg = NativeImage.read(is);
                int targetHeight = (int) (targetWidth * (9.0 / 16.0));
                NativeImage thumb = new NativeImage(targetWidth, targetHeight, true);
                fullImg.resizeSubRectTo(0, 0, fullImg.getWidth(), fullImg.getHeight(), thumb);
                fullImg.close();

                // Upload on Main Thread
                MinecraftClient.getInstance().execute(() -> {
                    // Check if cache still has this key (might have been cleared)
                    if (CACHE.containsKey(key) && CACHE.get(key).equals(id)) {
                        MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                                new net.minecraft.client.texture.NativeImageBackedTexture(thumb));
                    } else {
                        thumb.close(); // Clean up if no longer needed
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return id;
    }

    public static void clearCache() {
        CACHE.values().forEach(id -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
        CACHE.clear();
    }
}
