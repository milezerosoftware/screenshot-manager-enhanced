package com.milezerosoftware.mc.client.mixin;

import com.milezerosoftware.mc.config.ModConfig;

import net.minecraft.client.util.ScreenshotRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {
    // Intermediary: method_1660 [5, 6]
    @Inject(method = "getScreenshotFilename(Ljava/io/File;)Ljava/io/File;", at = @At("HEAD"), cancellable = true)
    private static void onGetScreenshotFilename(File gameDir, CallbackInfoReturnable<File> cir) {
        // Resolve our custom folder relative to the game directory
        File customDir = new File(gameDir, ModConfig.INSTANCE.customPath);

        if (!customDir.exists()) {
            customDir.mkdirs();
        }

        // Maintain standard vanilla naming: YYYY-MM-DD_HH.MM.SS.png [3]
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
        cir.setReturnValue(new File(customDir, timestamp + ".png"));
    }
}
