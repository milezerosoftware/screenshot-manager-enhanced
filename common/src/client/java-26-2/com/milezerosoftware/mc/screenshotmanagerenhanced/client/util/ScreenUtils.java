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
}
