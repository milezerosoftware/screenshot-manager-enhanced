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
import java.util.function.Consumer;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {
    private static final ThreadLocal<FilePathInfo> SCREENSHOT_FILE_INFO = new ThreadLocal<>();

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

    @Inject(method = "getScreenshotFilename(Ljava/io/File;)Ljava/io/File;", at = @At("RETURN"))
    private static void storeFilePathInfo(File gameDir, CallbackInfoReturnable<File> cir) {
        ModConfig config = ConfigManager.getInstance();
        if (!config.displayRelativePath) {
            SCREENSHOT_FILE_INFO.remove();
            return;
        }

        File screenshotFile = cir.getReturnValue();
        File screenshotsDir = new File(gameDir, "screenshots");

        String relativePath = ScreenshotPathGenerator.getScreenshotNotificationText(
                screenshotFile,
                screenshotsDir,
                true);

        SCREENSHOT_FILE_INFO.set(new FilePathInfo(screenshotFile, relativePath));
    }

    @ModifyVariable(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/client/gl/Framebuffer;Ljava/util/function/Consumer;)V", at = @At("HEAD"), argsOnly = true)
    private static Consumer<net.minecraft.text.Text> wrapMessageConsumer(Consumer<net.minecraft.text.Text> original) {
        FilePathInfo fileInfo = SCREENSHOT_FILE_INFO.get();

        if (fileInfo == null || !ConfigManager.getInstance().displayRelativePath) {
            return original;
        }

        // Return a wrapper that modifies the message before passing to the original
        // consumer
        return (text) -> {
            try {
                net.minecraft.text.Text modifiedText = modifyNotificationText(text, fileInfo);
                original.accept(modifiedText);
            } finally {
                SCREENSHOT_FILE_INFO.remove(); // Clean up after first message
            }
        };
    }

    private static net.minecraft.text.Text modifyNotificationText(net.minecraft.text.Text original,
            FilePathInfo fileInfo) {
        try {
            // Check if it's the success message
            if (original.getContent() instanceof net.minecraft.text.TranslatableTextContent translatable) {
                if ("screenshot.success".equals(translatable.getKey())) {
                    Object[] args = translatable.getArgs();
                    if (args.length > 0 && args[0] instanceof net.minecraft.text.Text fileComponent) {
                        // Create new component with the relative path but same style (click event)
                        net.minecraft.text.MutableText newFileComponent = net.minecraft.text.Text
                                .literal(fileInfo.relativePath)
                                .setStyle(fileComponent.getStyle());

                        // Return new success message with updated link text
                        return net.minecraft.text.Text.translatable("screenshot.success", newFileComponent);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return original;
    }

    private static class FilePathInfo {
        final File file;
        final String relativePath;

        FilePathInfo(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }
    }
}
