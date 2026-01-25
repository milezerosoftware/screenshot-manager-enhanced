package com.milezerosoftware.mc.screenshotmanagerenhanced.client.mixin;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotPathGenerator;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.WorldUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import net.minecraft.client.util.ScreenshotRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {

    // ThreadLocal to pass metadata task from Render thread to IO thread
    private static final ThreadLocal<com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.MetadataTask> PENDING_METADATA = new ThreadLocal<>();

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

        System.out.println(
                "[Screenshot Manager] Phase 1 (Render Thread): Generated file path: " + finalFile.getAbsolutePath());

        // Collect metadata NOW (on Render thread where we have access to world/player)
        // Then start async writer that polls for file existence
        if (config.embedMetadata) {
            System.out.println("[Screenshot Manager] Metadata embedding enabled, collecting metadata...");
            var metadata = com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataCollector
                    .collect();
            System.out.println("[Screenshot Manager] Metadata collected, starting async file watcher...");

            // Start async thread that waits for file to exist, then writes metadata
            final File targetFile = finalFile;
            Thread writerThread = new Thread(() -> {
                try {
                    // Wait for file to exist (poll every 100ms, timeout after 10 seconds)
                    int maxAttempts = 100;
                    int attempts = 0;
                    while (!targetFile.exists() && attempts < maxAttempts) {
                        Thread.sleep(100);
                        attempts++;
                    }

                    if (targetFile.exists()) {
                        System.out.println("[Screenshot Manager] File detected: " + targetFile.getAbsolutePath());
                        // Small delay to ensure file is fully written
                        Thread.sleep(200);
                        com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.MetadataHandler
                                .writeMetadataAsync(targetFile, metadata);
                    } else {
                        System.err.println(
                                "[Screenshot Manager] Timeout waiting for file: " + targetFile.getAbsolutePath());
                    }
                } catch (InterruptedException e) {
                    System.err.println("[Screenshot Manager] File watcher interrupted: " + e.getMessage());
                }
            }, "ScreenshotFileWatcher");
            writerThread.setDaemon(true);
            writerThread.start();
        } else {
            System.out.println("[Screenshot Manager] Metadata embedding disabled, skipping");
        }

        // Set the return value and cancel original method execution
        cir.setReturnValue(finalFile);
    }
}
