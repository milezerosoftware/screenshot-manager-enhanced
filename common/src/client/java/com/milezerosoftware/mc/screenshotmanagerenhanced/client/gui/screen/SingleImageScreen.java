package com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ScreenshotTextureManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ClipboardUtil;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.GalleryDirectoryScanner;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenUtils;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.*;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SingleImageScreen extends BaseOwoScreen<FlowLayout> {
    private static final int MAX_UPLOADS_PER_FRAME = 2;
    private static final int FULL_VIEW_TARGET_WIDTH = 1920;

    private final Screen parent;
    private final Path galleryDir;
    private final List<Path> images;
    private int currentIndex;
    private long viewGeneration = 0;
    private boolean navigatingToGallery = false;
    private boolean deleting = false;

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
        // Drain up to MAX_UPLOADS_PER_FRAME decoded textures on the render thread each frame.
        // Using owo-lib's Surface callback keeps this cross-version compatible across MC 1.21.x and 26.x.
        root.surface((context, component) -> {
            ScreenshotTextureManager.drainPendingUploads(MAX_UPLOADS_PER_FRAME);
            Surface.VANILLA_TRANSLUCENT.draw(context, component);
        });
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.verticalAlignment(VerticalAlignment.CENTER);

        if (images.isEmpty() || currentIndex < 0 || currentIndex >= images.size()) {
            this.onClose();
            return;
        }

        Path currentImage = images.get(currentIndex);
        long generation = ++this.viewGeneration;

        // Header
        var header = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        header.horizontalAlignment(HorizontalAlignment.CENTER);
        header.margins(Insets.top(10));

        header.child(UIComponents.label(Component.literal(currentImage.getFileName().toString())));
        header.child(UIComponents.label(Component.literal((currentIndex + 1) + " / " + images.size())));

        root.child(header);

        // Main Image
        var imageContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(75));
        imageContainer.horizontalAlignment(HorizontalAlignment.CENTER);
        imageContainer.verticalAlignment(VerticalAlignment.CENTER);

        int maxImgWidth = Math.max(1, (int) (this.width * 0.80));
        int maxImgHeight = Math.max(1, (int) (this.height * 0.65));

        int[] placeholderFit = GalleryDirectoryScanner.fitWithinBounds(16, 9, maxImgWidth, maxImgHeight);
        Identifier placeholderId = ScreenshotTextureManager.getPlaceholderId();
        var placeholder = UIComponents.texture(placeholderId, 0, 0, 1, 1, 1, 1);
        placeholder.sizing(Sizing.fixed(placeholderFit[0]), Sizing.fixed(placeholderFit[1]));

        imageContainer.child(placeholder);
        root.child(imageContainer);

        ScreenshotTextureManager.requestThumbnail(currentImage, FULL_VIEW_TARGET_WIDTH, result -> {
            if (generation != this.viewGeneration
                    || this.uiAdapter == null
                    || ScreenUtils.getCurrentScreen() != this) {
                return;
            }
            int[] fitted = GalleryDirectoryScanner.fitWithinBounds(
                    result.width(), result.height(), maxImgWidth, maxImgHeight);
            var loadedTexture = UIComponents.texture(
                    result.id(), 0, 0, result.width(), result.height(), result.width(), result.height());
            loadedTexture.sizing(Sizing.fixed(fitted[0]), Sizing.fixed(fitted[1]));
            imageContainer.clearChildren();
            imageContainer.child(loadedTexture);
        });

        // Prefetch adjacent images so Prev/Next navigation hits the warm LRU cache
        if (currentIndex > 0) {
            ScreenshotTextureManager.prefetch(images.get(currentIndex - 1), FULL_VIEW_TARGET_WIDTH);
        }
        if (currentIndex < images.size() - 1) {
            ScreenshotTextureManager.prefetch(images.get(currentIndex + 1), FULL_VIEW_TARGET_WIDTH);
        }

        // Footer / Controls
        int minButtonWidth = 260; // Minimal width to fit all buttons
        int controlWidth = Math.max(placeholderFit[0], minButtonWidth);

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
        leftGroup.child(UIComponents.button(Component.literal("Back"), b -> {
            this.navigatingToGallery = true;
            ScreenUtils.setScreen(new GalleryScreen(this.parent, this.galleryDir));
        }).sizing(Sizing.fixed(actionBtnWidth), Sizing.fixed(btnHeight)));

        // Copy
        leftGroup.child(UIComponents.button(Component.literal("Copy"), b -> {
            if (currentIndex < 0 || currentIndex >= images.size()) {
                return;
            }
            Path pathToCopy = images.get(currentIndex);
            CompletableFuture.supplyAsync(() -> ClipboardUtil.copyImageToClipboard(pathToCopy))
                    .thenAccept(success -> Minecraft.getInstance().execute(() -> {
                        if (Boolean.TRUE.equals(success)) {
                            SystemToast.add(
                                    ScreenUtils.getToastManager(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("Copied!"),
                                    Component.literal("Image copied to clipboard"));
                        }
                    }));
        }).sizing(Sizing.fixed(actionBtnWidth), Sizing.fixed(btnHeight)));

        // Delete
        leftGroup.child(UIComponents.button(Component.literal("Delete"), b -> {
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
        var prevBtn = UIComponents.button(Component.literal("⏮"), b -> {
            if (currentIndex > 0) {
                currentIndex--;
                rebuild();
            }
        });
        prevBtn.sizing(Sizing.fixed(navBtnWidth), Sizing.fixed(btnHeight));
        prevBtn.active = currentIndex > 0;
        rightGroup.child(prevBtn);

        // Next (Skip Forward)
        var nextBtn = UIComponents.button(Component.literal("⏭"), b -> {
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
        ScreenshotTextureManager.cancelPending();
        this.uiAdapter.rootComponent.clearChildren();
        build(this.uiAdapter.rootComponent);
    }

    private void deleteCurrentImage() {
        if (images.isEmpty() || deleting || currentIndex < 0 || currentIndex >= images.size())
            return;
        deleting = true;
        Path path = images.get(currentIndex);

        CompletableFuture.supplyAsync(() -> {
            try {
                boolean trashed = false;
                // Try Native Trash first
                try {
                    if (java.awt.Desktop.isDesktopSupported()
                            && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                        trashed = java.awt.Desktop.getDesktop().moveToTrash(path.toFile());
                    }
                } catch (Exception e) {
                    // Ignore AWT errors (headless etc)
                }

                if (!trashed) {
                    Files.deleteIfExists(path);
                }
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }).thenAccept(deleted -> Minecraft.getInstance().execute(() -> {
            deleting = false;
            if (Boolean.TRUE.equals(deleted)) {
                ScreenshotTextureManager.evict(path);
                if (this.uiAdapter == null || ScreenUtils.getCurrentScreen() != this) {
                    return;
                }
                images.remove(path);
                if (images.isEmpty()) {
                    this.onClose(); // Return to gallery
                } else {
                    if (currentIndex >= images.size()) {
                        currentIndex = images.size() - 1;
                    }
                    rebuild(); // Refresh UI for next image
                }
            } else if (this.uiAdapter != null && ScreenUtils.getCurrentScreen() == this) {
                SystemToast.add(
                        ScreenUtils.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Error"),
                        Component.literal("Failed to delete file"));
            }
        }));
    }

    @Override
    public void onClose() {
        if (parent != null) {
            this.navigatingToGallery = true;
            ScreenUtils.setScreen(this.minecraft, new GalleryScreen(this.parent, this.galleryDir));
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        super.removed();
        this.viewGeneration++;
        this.uiAdapter = null;
        if (this.navigatingToGallery) {
            this.navigatingToGallery = false;
            ScreenshotTextureManager.cancelPending();
        } else {
            ScreenshotTextureManager.clearCache();
        }
    }
}
