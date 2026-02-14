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

    public static Identifier getOrCreateThumbnail(Path path, int targetWidth) {
        String key = path.toString() + "_" + targetWidth;
        if (CACHE.containsKey(key))
            return CACHE.get(key);

        try (InputStream is = Files.newInputStream(path)) {
            NativeImage fullImg = NativeImage.read(is);

            int targetHeight = (int) (targetWidth * (9.0 / 16.0));
            NativeImage thumb = new NativeImage(targetWidth, targetHeight, true);
            fullImg.resizeSubRectTo(0, 0, fullImg.getWidth(), fullImg.getHeight(), thumb);

            // Use the mod ID as the namespace and include size in path to distinguish
            // textures
            Identifier id = Identifier.of("screenshot-manager-enhanced",
                    "thumb/" + targetWidth + "_"
                            + path.getFileName().toString().toLowerCase().replaceAll("[^a-z0-9/._-]", "_"));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                    new net.minecraft.client.texture.NativeImageBackedTexture(thumb));

            CACHE.put(key, id);
            fullImg.close();
            return id;
        } catch (Exception e) {
            e.printStackTrace();
            return Identifier.of("minecraft", "textures/item/barrier.png");
        }
    }

    public static void clearCache() {
        CACHE.values().forEach(id -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
        CACHE.clear();
    }
}
