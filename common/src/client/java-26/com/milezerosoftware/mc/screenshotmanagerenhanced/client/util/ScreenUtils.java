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

    public static Screen getCurrentScreen() {
        Minecraft client = Minecraft.getInstance();
        return client != null ? client.screen : null;
    }

    public static ToastManager getToastManager() {
        return Minecraft.getInstance().getToastManager();
    }

    public static void setClipboard(String text) {
        if (text == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(text);
        }
    }
}
