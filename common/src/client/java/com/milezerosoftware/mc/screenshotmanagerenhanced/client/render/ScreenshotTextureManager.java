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

        // Register placeholder
        NativeImage placeholder = new NativeImage(1, 1, true);
        registerTextureSafe(id, placeholder);

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
                        registerTextureSafe(id, thumb);
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

    private static void registerTextureSafe(Identifier id, NativeImage image) {
        try {
            Class<?> textureClass = net.minecraft.client.texture.NativeImageBackedTexture.class;

            // Try explicit (NativeImage) - 1.20, 1.21.1-1.21.4
            try {
                java.lang.reflect.Constructor<?> c = textureClass.getConstructor(NativeImage.class);
                Object texture = c.newInstance(image);
                MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                        (net.minecraft.client.texture.AbstractTexture) texture);
                return;
            } catch (NoSuchMethodException e) {
                // Ignore
            }

            // Iterate constructors for advanced matching (1.21.5+)
            for (java.lang.reflect.Constructor<?> c : textureClass.getConstructors()) {
                Class<?>[] types = c.getParameterTypes();

                // Case: (Supplier, NativeImage) - 1.21.5?
                if (types.length == 2 && types[1] == NativeImage.class
                        && java.util.function.Supplier.class.isAssignableFrom(types[0])) {
                    java.util.function.Supplier<String> sup = () -> "dynamic_" + id.getPath();
                    Object texture = c.newInstance(sup, image);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                            (net.minecraft.client.texture.AbstractTexture) texture);
                    return;
                }
            }

            System.err.println("[ScreenshotManager] Could not find suitable NativeImageBackedTexture constructor!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void clearCache() {
        CACHE.values().forEach(id -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
        CACHE.clear();
    }
}
