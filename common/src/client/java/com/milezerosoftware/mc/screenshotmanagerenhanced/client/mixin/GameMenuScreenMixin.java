package com.milezerosoftware.mc.screenshotmanagerenhanced.client.mixin;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen.GalleryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(at = @At("TAIL"), method = "initWidgets")
    private void addGalleryButton(CallbackInfo ci) {
        this.addDrawableChild(ButtonWidget.builder(Text.literal(" 📷 "), button -> {

            // todo: change the base path to the mod's set sceenshot folder
            Path path = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");

            // todo: fix the botton size based on the emoji on line 24
            MinecraftClient.getInstance().setScreen(new GalleryScreen(path));
        }).dimensions(this.width / 2 + 104, this.height / 4 + 96 + -16, 98, 20).build());
        // Adjusted width to 98 to fit text, positioned to the right of standard buttons
        // if layout permits
    }
}
