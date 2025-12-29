package com.milezerosoftware.mc.client.mixin;

import com.milezerosoftware.mc.client.util.WorldUtils;
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
        // Get the sanitized world/server name
        String safeWorldId = WorldUtils.getSafeWorldId();

        // Construct the dynamic path: .../screenshots/{worldId}/
        File worldScreenshotsDir = new File(new File(gameDir, "screenshots"), safeWorldId);

        // Ensure the directory exists
        if (!worldScreenshotsDir.exists()) {
            worldScreenshotsDir.mkdirs();
        }

        // Maintain standard vanilla naming: YYYY-MM-DD_HH.MM.SS.png
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
        File screenshotFile = new File(worldScreenshotsDir, timestamp + ".png");

        // Set the return value and cancel original method execution
        cir.setReturnValue(screenshotFile);
    }
}
