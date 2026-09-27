package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

public class ScreenUtils {

    public static void setScreen(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gui != null) {
            client.gui.setScreen(screen);
        }
    }

    public static void setScreen(Minecraft client, Screen screen) {
        if (client != null && client.gui != null) {
            client.gui.setScreen(screen);
        } else {
            setScreen(screen);
        }
    }

    public static ToastManager getToastManager() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.gui != null ? client.gui.toastManager() : null;
    }

    public static void openPath(java.nio.file.Path path) {
        try {
            Class<?> blaze3d = Class.forName("com.mojang.blaze3d.Blaze3D");
            java.lang.reflect.Method m = blaze3d.getMethod("openPath", java.nio.file.Path.class);
            m.invoke(null, path);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> utilClass = Class.forName("net.minecraft.util.Util");
            Object platform = utilClass.getMethod("getPlatform").invoke(null);
            platform.getClass().getMethod("openPath", java.nio.file.Path.class).invoke(platform, path);
            return;
        } catch (Throwable ignored) {
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(path.toFile());
            }
        } catch (Throwable ignored) {
        }
    }
}
