package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

public class ScreenUtils {

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static void setScreen(Minecraft client, Screen screen) {
        if (client != null) {
            client.setScreen(screen);
        } else {
            Minecraft.getInstance().setScreen(screen);
        }
    }

    public static ToastManager getToastManager() {
        return Minecraft.getInstance().getToastManager();
    }
}
