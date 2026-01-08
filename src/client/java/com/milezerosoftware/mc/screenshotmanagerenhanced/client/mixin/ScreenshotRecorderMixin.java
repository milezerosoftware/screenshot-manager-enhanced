package com.milezerosoftware.mc.screenshotmanagerenhanced.client.mixin;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotPathGenerator;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.WorldUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
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
        String rawWorldId = WorldUtils.getWorldId();
        String safeWorldId = WorldUtils.sanitize(rawWorldId);
        String dimension = WorldUtils.getDimension();
        ModConfig config = ConfigManager.getInstance();

        // Use the centralized path generator
        File screenshotFile = ScreenshotPathGenerator.getScreenshotDirectory(
                gameDir,
                config,
                rawWorldId,
                safeWorldId,
                dimension,
                new Date());

        // Ensure the directory exists
        if (!screenshotFile.exists()) {
            screenshotFile.mkdirs();
        }

        // Maintain standard vanilla naming: YYYY-MM-DD_HH.MM.SS.png
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
        File finalFile = new File(screenshotFile, timestamp + ".png");

        // Handle filename collisions by appending a suffix
        int i = 1;
        while (finalFile.exists()) {
            finalFile = new File(screenshotFile, timestamp + "_" + (i++) + ".png");
        }

        // Set the return value and cancel original method execution
        cir.setReturnValue(finalFile);
    }
}
