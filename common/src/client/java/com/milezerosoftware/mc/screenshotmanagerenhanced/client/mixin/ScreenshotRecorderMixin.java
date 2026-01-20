package com.milezerosoftware.mc.screenshotmanagerenhanced.client.mixin;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotPathGenerator;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.WorldUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import net.minecraft.client.util.ScreenshotRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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

    @ModifyArg(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/client/gl/Framebuffer;Ljava/util/function/Consumer;)V", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"), index = 0)
    private static net.minecraft.text.Text modifyNotificationText(net.minecraft.text.Text original) {
        ModConfig config = ConfigManager.getInstance();
        if (!config.displayRelativePath) {
            return original;
        }

        try {
            // Extract the ClickEvent to get the file path
            net.minecraft.text.Style style = original.getStyle();
            net.minecraft.text.ClickEvent clickEvent = style != null ? style.getClickEvent() : null;

            if (clickEvent != null && clickEvent.getAction() == net.minecraft.text.ClickEvent.Action.OPEN_FILE) {
                String filePath = clickEvent.getValue();
                File screenshotFile = new File(filePath);

                // Determine the base folder to relativize against.
                // If it's absolute, try to find "screenshots" in the path or use parent.
                File screenshotsDir = new File("screenshots"); // Default fallback
                if (screenshotFile.isAbsolute()) {
                    String path = screenshotFile.getAbsolutePath();
                    int index = path.indexOf("screenshots");
                    if (index != -1) {
                        // +11 covers "screenshots" length
                        String basePath = path.substring(0, index + 11);
                        screenshotsDir = new File(basePath);
                    } else {
                        screenshotsDir = screenshotFile.getParentFile();
                    }
                }

                String displayDecoratedPath = ScreenshotPathGenerator.getScreenshotNotificationText(
                        screenshotFile,
                        screenshotsDir,
                        config.displayRelativePath);

                // Replace the text content if it's a translatable "screenshot.success"
                if (original.getContent() instanceof net.minecraft.text.TranslatableTextContent translatable) {
                    if ("screenshot.success".equals(translatable.getKey())) {
                        Object[] args = translatable.getArgs();
                        if (args.length > 0 && args[0] instanceof net.minecraft.text.Text fileComponent) {
                            // Create new component with the relative path but same style (click event)
                            net.minecraft.text.MutableText newFileComponent = net.minecraft.text.Text
                                    .literal(displayDecoratedPath)
                                    .setStyle(fileComponent.getStyle());

                            // Return new success message with updated link text
                            return net.minecraft.text.Text.translatable("screenshot.success", newFileComponent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return original;
    }
}
