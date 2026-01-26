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

/**
 * Mixin for {@link ScreenshotRecorder} that intercepts screenshot file path
 * generation.
 * 
 * <p>
 * This mixin provides two key features:
 * </p>
 * <ul>
 * <li>Custom screenshot directory organization based on world/server name</li>
 * <li>XMP metadata embedding into screenshot PNG files</li>
 * </ul>
 * 
 * <p>
 * The mixin intercepts the {@code getScreenshotFilename} method to redirect
 * screenshots to organized subdirectories and optionally embed metadata.
 * </p>
 */
@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {

    private static final int FILE_POLL_INTERVAL_MS = 100;
    private static final int FILE_POLL_MAX_ATTEMPTS = 100;
    private static final int FILE_WRITE_DELAY_MS = 200;

    /**
     * Intercepts screenshot filename generation to provide custom paths and
     * metadata embedding.
     * 
     * <p>
     * This injection runs at the HEAD of {@code getScreenshotFilename}, cancelling
     * the original method and providing a custom file path based on the current
     * world or server context.
     * </p>
     * 
     * <p>
     * When metadata embedding is enabled, this method also:
     * </p>
     * <ol>
     * <li>Collects metadata from the current game state (on Render thread)</li>
     * <li>Spawns a daemon thread to wait for the file to be written</li>
     * <li>Triggers async metadata embedding once the file exists</li>
     * </ol>
     *
     * @param gameDir The game directory (usually .minecraft)
     * @param cir     Callback info for returning the custom file path
     */
    @Inject(method = "getScreenshotFilename(Ljava/io/File;)Ljava/io/File;", at = @At("HEAD"), cancellable = true)
    private static void onGetScreenshotFilename(File gameDir, CallbackInfoReturnable<File> cir) {
        // Get the sanitized world/server name
        String rawWorldId = WorldUtils.getWorldId();
        String safeWorldId = WorldUtils.sanitize(rawWorldId);
        String dimension = WorldUtils.getDimension();
        ModConfig config = ConfigManager.getInstance();

        // Use the centralized path generator
        File screenshotDir = ScreenshotPathGenerator.getScreenshotDirectory(
                gameDir,
                config,
                rawWorldId,
                safeWorldId,
                dimension,
                new Date());

        // Ensure the directory exists
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }

        // Maintain standard vanilla naming: YYYY-MM-DD_HH.MM.SS.png
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
        File finalFile = new File(screenshotDir, timestamp + ".png");

        // Handle filename collisions by appending a suffix
        int i = 1;
        while (finalFile.exists()) {
            finalFile = new File(screenshotDir, timestamp + "_" + (i++) + ".png");
        }

        // Collect metadata and start async writer if enabled
        if (config.embedMetadata) {
            collectAndEmbedMetadata(finalFile);
        }

        // Set the return value and cancel original method execution
        cir.setReturnValue(finalFile);
    }

    /**
     * Collects metadata on the Render thread and spawns an async writer.
     * 
     * <p>
     * Metadata must be collected on the Render thread because it requires
     * access to game state (world, player, etc.). The actual file writing
     * is done asynchronously to avoid blocking.
     * </p>
     *
     * @param targetFile The screenshot file that will be created
     */
    private static void collectAndEmbedMetadata(File targetFile) {
        var metadata = com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataCollector
                .collect();

        // Start async thread that waits for file to exist, then writes metadata
        Thread writerThread = new Thread(() -> {
            try {
                // Wait for file to exist (poll every 100ms, timeout after 10 seconds)
                int attempts = 0;
                while (!targetFile.exists() && attempts < FILE_POLL_MAX_ATTEMPTS) {
                    Thread.sleep(FILE_POLL_INTERVAL_MS);
                    attempts++;
                }

                if (targetFile.exists()) {
                    // Small delay to ensure file is fully written
                    Thread.sleep(FILE_WRITE_DELAY_MS);
                    com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.MetadataHandler
                            .writeMetadataAsync(targetFile, metadata);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "ScreenshotFileWatcher");

        writerThread.setDaemon(true);
        writerThread.start();
    }
}
