package com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ScreenshotTextureManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ClipboardUtil;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.*;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SingleImageScreen extends BaseOwoScreen<FlowLayout> {

    private final Screen parent;
    private final Path galleryDir;
    private final List<Path> images;
    private int currentIndex;

    public SingleImageScreen(Screen parent, List<Path> images, int index, Path galleryDir) {
        this.parent = parent;
        this.images = new ArrayList<>(images); // Copy list
        this.currentIndex = index;
        this.galleryDir = galleryDir;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.verticalAlignment(VerticalAlignment.CENTER);

        if (images.isEmpty() || currentIndex < 0 || currentIndex >= images.size()) {
            this.close();
            return;
        }

        Path currentImage = images.get(currentIndex);

        // Header
        var header = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        header.horizontalAlignment(HorizontalAlignment.CENTER);
        header.margins(Insets.top(10));

        header.child(UIComponents.label(Text.literal(currentImage.getFileName().toString())));
        header.child(UIComponents.label(Text.literal((currentIndex + 1) + " / " + images.size())));

        root.child(header);

        // Main Image
        var imageContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(75));
        imageContainer.horizontalAlignment(HorizontalAlignment.CENTER);
        imageContainer.verticalAlignment(VerticalAlignment.CENTER);

        Identifier textureId = ScreenshotTextureManager.getOrCreateThumbnail(currentImage, 1920);

        // Calculate size: 80% of screen width, 16:9
        int imgWidth = (int) (this.width * 0.80);
        int imgHeight = (int) (imgWidth * (9.0 / 16.0));

        // Ensure it fits vertically too
        if (imgHeight > this.height * 0.65) {
            imgHeight = (int) (this.height * 0.65);
            imgWidth = (int) (imgHeight * (16.0 / 9.0));
        }

        int aspectW = 16;
        int aspectH = 9;

        var texture = UIComponents.texture(textureId, 0, 0, aspectW, aspectH, aspectW, aspectH);
        texture.sizing(Sizing.fixed(imgWidth), Sizing.fixed(imgHeight));

        imageContainer.child(texture);
        root.child(imageContainer);

        // Footer / Controls
        int minButtonWidth = 260; // Minimal width to fit all buttons
        int controlWidth = Math.max(imgWidth, minButtonWidth);

        // Use Stack for precise edge pinning (matches GalleryScreen)
        var footer = UIContainers.stack(Sizing.fixed(controlWidth), Sizing.fixed(30));
        footer.verticalAlignment(VerticalAlignment.CENTER);

        /**
         * Left Group: [Back] [Copy] [Delete]
         */
        var leftGroup = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        leftGroup.gap(5);

        int btnHeight = 20;
        int actionBtnWidth = 50;

        // Back
        leftGroup.child(UIComponents.button(Text.literal("Back"), b -> {
            MinecraftClient.getInstance().setScreen(new GalleryScreen(this.parent, this.galleryDir));
        }).sizing(Sizing.fixed(actionBtnWidth), Sizing.fixed(btnHeight)));

        // Copy
        leftGroup.child(UIComponents.button(Text.literal("Copy"), b -> {
            boolean success = ClipboardUtil.copyImageToClipboard(images.get(currentIndex));
            if (success) {
                SystemToast.add(
                        MinecraftClient.getInstance().getToastManager(),
                        SystemToast.Type.PERIODIC_NOTIFICATION,
                        Text.literal("Copied!"),
                        Text.literal("Image copied to clipboard"));
            }
        }).sizing(Sizing.fixed(actionBtnWidth), Sizing.fixed(btnHeight)));

        // Delete
        leftGroup.child(UIComponents.button(Text.literal("Delete"), b -> {
            deleteCurrentImage();
        }).sizing(Sizing.fixed(actionBtnWidth), Sizing.fixed(btnHeight)));

        // Pin Left
        leftGroup.positioning(Positioning.relative(0, 50));
        footer.child(leftGroup);

        /**
         * Right Group: [Prev] [Next]
         */
        var rightGroup = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        rightGroup.gap(5);

        int navBtnWidth = 30;

        // Prev (Skip Backward)
        var prevBtn = UIComponents.button(Text.literal("⏮"), b -> {
            if (currentIndex > 0) {
                currentIndex--;
                rebuild();
            }
        });
        prevBtn.sizing(Sizing.fixed(navBtnWidth), Sizing.fixed(btnHeight));
        prevBtn.active = currentIndex > 0;
        rightGroup.child(prevBtn);

        // Next (Skip Forward)
        var nextBtn = UIComponents.button(Text.literal("⏭"), b -> {
            if (currentIndex < images.size() - 1) {
                currentIndex++;
                rebuild();
            }
        });
        nextBtn.sizing(Sizing.fixed(navBtnWidth), Sizing.fixed(btnHeight));
        nextBtn.active = currentIndex < images.size() - 1;
        rightGroup.child(nextBtn);

        // Pin Right
        rightGroup.positioning(Positioning.relative(100, 50));
        footer.child(rightGroup);

        root.child(footer);
    }

    private void rebuild() {
        ScreenshotTextureManager.clearCache();
        this.uiAdapter.rootComponent.clearChildren();
        build(this.uiAdapter.rootComponent);
    }

    private void deleteCurrentImage() {
        if (images.isEmpty())
            return;
        Path path = images.get(currentIndex);
        try {
            boolean trashed = false;
            // Try Native Trash first
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                    java.awt.Desktop.getDesktop().moveToTrash(path.toFile());
                    trashed = true;
                }
            } catch (Exception e) {
                // Ignore AWT errors (headless etc)
            }

            if (!trashed) {
                Files.delete(path);
            }

            images.remove(currentIndex);
            ScreenshotTextureManager.clearCache();

            if (images.isEmpty()) {
                this.close(); // Return to gallery
            } else {
                if (currentIndex >= images.size()) {
                    currentIndex = images.size() - 1;
                }
                rebuild(); // Refresh UI for next image
            }
        } catch (IOException e) {
            e.printStackTrace();
            SystemToast.add(
                    MinecraftClient.getInstance().getToastManager(),
                    SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("Error"),
                    Text.literal("Failed to delete file"));
        }
    }

    @Override
    public void close() {
        if (parent != null) {
            this.client.setScreen(new GalleryScreen(this.parent, this.galleryDir));
        } else {
            super.close();
        }
    }

    @Override
    public void removed() {
        super.removed();
        ScreenshotTextureManager.clearCache();
    }
}
