package com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ScreenshotTextureManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ClipboardUtil;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.GalleryDirectoryScanner;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.GalleryNavigation;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataFormatter;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataFormatter.InspectorLayout;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataFormatter.MetadataRow;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader.MetadataReadResult;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.*;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SingleImageScreen extends BaseOwoScreen<FlowLayout> {
    private static final int MAX_UPLOADS_PER_FRAME = 2;
    private static final int FULL_VIEW_TARGET_WIDTH = 1920;
    private static final int MAX_METADATA_CACHE_ENTRIES = 64;
    private static final long COPIED_FEEDBACK_MS = 1500L;

    private static boolean showMetadataPanel = false; // Session-persistent like GalleryScreen.columns

    private record MetadataCacheKey(Path path, long lastModifiedMillis) {
        MetadataCacheKey {
            path = path.normalize();
        }
    }

    private static final Map<MetadataCacheKey, MetadataReadResult> METADATA_CACHE =
            new LinkedHashMap<>(MAX_METADATA_CACHE_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<MetadataCacheKey, MetadataReadResult> eldest) {
                    return size() > MAX_METADATA_CACHE_ENTRIES;
                }
            };

    private static final ExecutorService METADATA_EXECUTOR = Executors.newFixedThreadPool(
            2,
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ScreenshotMetadataLoader-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                }
            });

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

        // Main Image (+ optional Metadata Inspector Panel)
        InspectorLayout layout = ScreenshotMetadataFormatter.computeLayout(this.width, this.height, showMetadataPanel);
        int maxImgWidth = layout.maxImageWidth();
        int maxImgHeight = layout.maxImageHeight();

        int[] placeholderFit = GalleryDirectoryScanner.fitWithinBounds(16, 9, maxImgWidth, maxImgHeight);
        Identifier placeholderId = ScreenshotTextureManager.getPlaceholderId();
        var placeholder = UIComponents.texture(placeholderId, 0, 0, 1, 1, 1, 1);
        placeholder.sizing(Sizing.fixed(placeholderFit[0]), Sizing.fixed(placeholderFit[1]));

        FlowLayout imageContainer;
        if (!showMetadataPanel) {
            imageContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(75));
            imageContainer.horizontalAlignment(HorizontalAlignment.CENTER);
            imageContainer.verticalAlignment(VerticalAlignment.CENTER);
            imageContainer.child(placeholder);
            root.child(imageContainer);
        } else {
            int panelBgColor = layout.overlayMode() ? 0xDD121212 : 0xCC121212;
            Surface inspectorSurface = (context, component) -> {
                Surface.flat(panelBgColor).draw(context, component);
                Surface.outline(0xFF404040).draw(context, component);
            };

            var panelBody = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            panelBody.padding(Insets.of(8));
            panelBody.gap(4);

            var inspectorScroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.fill(100), panelBody);

            var inspectorCard = UIContainers.verticalFlow(Sizing.fixed(layout.panelWidth()), Sizing.fill(100));
            inspectorCard.surface(inspectorSurface);
            inspectorCard.child(inspectorScroll);

            if (layout.overlayMode()) {
                var middleStack = UIContainers.stack(Sizing.fill(100), Sizing.fill(75));
                imageContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
                imageContainer.horizontalAlignment(HorizontalAlignment.CENTER);
                imageContainer.verticalAlignment(VerticalAlignment.CENTER);
                imageContainer.child(placeholder);

                inspectorCard.positioning(Positioning.relative(100, 50));
                inspectorCard.margins(Insets.right(6));

                middleStack.child(imageContainer);
                middleStack.child(inspectorCard);
                root.child(middleStack);
            } else {
                var middleFlow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(75));
                middleFlow.padding(Insets.horizontal(ScreenshotMetadataFormatter.HORIZONTAL_MARGIN));
                middleFlow.gap(ScreenshotMetadataFormatter.PANEL_GAP);
                middleFlow.horizontalAlignment(HorizontalAlignment.CENTER);
                middleFlow.verticalAlignment(VerticalAlignment.CENTER);

                imageContainer = UIContainers.verticalFlow(Sizing.fixed(layout.leftPaneWidth()), Sizing.fill(100));
                imageContainer.horizontalAlignment(HorizontalAlignment.CENTER);
                imageContainer.verticalAlignment(VerticalAlignment.CENTER);
                imageContainer.child(placeholder);

                middleFlow.child(imageContainer);
                middleFlow.child(inspectorCard);
                root.child(middleFlow);
            }

            int textMaxWidth = Math.max(60, layout.panelWidth() - 28);
            loadMetadataIntoPanel(currentImage, generation, panelBody, textMaxWidth);
        }

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

        // Footer / Controls (stable width regardless of whether Info panel is open)
        int[] fullPlaceholderFit = GalleryDirectoryScanner.fitWithinBounds(
                16, 9, Math.max(1, (int) (this.width * 0.80)), maxImgHeight);
        int minButtonWidth = 315; // Minimal width to fit [Back][Copy][Delete][Info] + [Prev][Next]
        int controlWidth = ScreenshotMetadataFormatter.computeFooterWidth(
                this.width, fullPlaceholderFit[0], minButtonWidth);

        // Use Stack for precise edge pinning (matches GalleryScreen)
        var footer = UIContainers.stack(Sizing.fixed(controlWidth), Sizing.fixed(30));
        footer.verticalAlignment(VerticalAlignment.CENTER);

        /**
         * Left Group: [Back] [Copy] [Delete] [Info]
         */
        var leftGroup = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        leftGroup.gap(5);

        int btnHeight = 20;
        int actionBtnWidth = 50;
        int infoBtnWidth = 45;

        // Back
        leftGroup.child(UIComponents.button(Component.literal("Back"), b -> {
            this.navigatingToGallery = true;
            int returnIndex = images.isEmpty() ? -1 : this.currentIndex;
            ScreenUtils.setScreen(new GalleryScreen(this.parent, this.galleryDir, returnIndex));
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

        // Info (always visible regardless of embedMetadata setting)
        Component infoLabel = showMetadataPanel
                ? Component.literal("Info").withStyle(ChatFormatting.GOLD)
                : Component.literal("Info");
        var infoBtn = UIComponents.button(infoLabel, b -> toggleMetadataPanel());
        infoBtn.sizing(Sizing.fixed(infoBtnWidth), Sizing.fixed(btnHeight));
        infoBtn.tooltip(Component.literal("Toggle Metadata (I)"));
        leftGroup.child(infoBtn);

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
            navigateBy(-1);
        });
        prevBtn.sizing(Sizing.fixed(navBtnWidth), Sizing.fixed(btnHeight));
        prevBtn.active = currentIndex > 0;
        rightGroup.child(prevBtn);

        // Next (Skip Forward)
        var nextBtn = UIComponents.button(Component.literal("⏭"), b -> {
            navigateBy(1);
        });
        nextBtn.sizing(Sizing.fixed(navBtnWidth), Sizing.fixed(btnHeight));
        nextBtn.active = currentIndex < images.size() - 1;
        rightGroup.child(nextBtn);

        // Pin Right
        rightGroup.positioning(Positioning.relative(100, 50));
        footer.child(rightGroup);

        root.child(footer);
    }

    private void toggleMetadataPanel() {
        if (deleting) {
            return;
        }
        showMetadataPanel = !showMetadataPanel;
        rebuild();
    }

    private void loadMetadataIntoPanel(Path imagePath, long generation, FlowLayout panelBody, int textMaxWidth) {
        MetadataReadResult warmCached = getCachedMetadataByPath(imagePath);
        if (warmCached != null) {
            populateMetadataPanel(panelBody, warmCached, textMaxWidth, generation);
        } else {
            panelBody.child(UIComponents.label(Component.literal("Loading metadata...").withStyle(ChatFormatting.GRAY))
                    .maxWidth(textMaxWidth));
        }

        CompletableFuture.supplyAsync(() -> getOrReadMetadata(imagePath), METADATA_EXECUTOR)
                .thenAccept(result -> Minecraft.getInstance().execute(() -> {
                    if (generation != this.viewGeneration
                            || this.uiAdapter == null
                            || ScreenUtils.getCurrentScreen() != this) {
                        return;
                    }
                    if (warmCached != null && warmCached.equals(result)) {
                        return;
                    }
                    populateMetadataPanel(panelBody, result, textMaxWidth, generation);
                }));
    }

    private void populateMetadataPanel(
            FlowLayout panelBody,
            MetadataReadResult result,
            int textMaxWidth,
            long generation) {
        panelBody.clearChildren();
        clearDismountedFocus();

        // Section 1: Minecraft Metadata
        panelBody.child(UIComponents.label(
                Component.literal("Minecraft Metadata").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .maxWidth(textMaxWidth));

        List<MetadataRow> mcRows = ScreenshotMetadataFormatter.buildMinecraftRows(
                result != null ? result.metadata() : null);

        if (mcRows.isEmpty()) {
            panelBody.child(UIComponents.label(
                    Component.literal("No embedded Minecraft metadata found.").withStyle(ChatFormatting.GRAY))
                    .maxWidth(textMaxWidth));

            if (!ConfigManager.getInstance().embedMetadata) {
                panelBody.child(UIComponents.label(
                        Component.literal("Tip: Enable \"Embed Screenshot Metadata\" in Settings to embed world info in new screenshots.")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                        .maxWidth(textMaxWidth)
                        .margins(Insets.top(2)));
            }
        } else {
            for (MetadataRow row : mcRows) {
                panelBody.child(createMetadataRowComponent(row, textMaxWidth, generation));
            }
        }

        // Divider
        var divider = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface(Surface.flat(0xFF3A3A3A));
        divider.margins(Insets.vertical(4));
        panelBody.child(divider);

        // Section 2: File Info
        panelBody.child(UIComponents.label(
                Component.literal("File Info").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .maxWidth(textMaxWidth));

        List<MetadataRow> fileRows = ScreenshotMetadataFormatter.buildFileInfoRows(
                result != null ? result.fileInfo() : null);

        if (fileRows.isEmpty()) {
            panelBody.child(UIComponents.label(
                    Component.literal("File info unavailable.").withStyle(ChatFormatting.GRAY))
                    .maxWidth(textMaxWidth));
        } else {
            for (MetadataRow row : fileRows) {
                panelBody.child(createMetadataRowComponent(row, textMaxWidth, generation));
            }
        }
    }

    private UIComponent createMetadataRowComponent(MetadataRow row, int textMaxWidth, long generation) {
        var container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.gap(1);
        container.margins(Insets.bottom(2));

        if (row.isCopyable()) {
            var headerRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            headerRow.verticalAlignment(VerticalAlignment.CENTER);
            headerRow.gap(6);

            headerRow.child(UIComponents.label(
                    Component.literal(row.label()).withStyle(ChatFormatting.GRAY)));

            final int[] copyClickGen = {0};
            var copyBtn = UIComponents.button(Component.literal("Copy"), btn -> {
                int token = ++copyClickGen[0];
                ScreenUtils.setClipboard(row.copyValue());
                clearComponentFocus();
                btn.setMessage(Component.literal("Copied!").withStyle(ChatFormatting.GREEN));
                CompletableFuture.delayedExecutor(COPIED_FEEDBACK_MS, TimeUnit.MILLISECONDS)
                        .execute(() -> Minecraft.getInstance().execute(() -> {
                            if (token == copyClickGen[0]
                                    && generation == this.viewGeneration
                                    && this.uiAdapter != null
                                    && ScreenUtils.getCurrentScreen() == this
                                    && btn.root() == this.uiAdapter.rootComponent) {
                                btn.setMessage(Component.literal("Copy"));
                            }
                        }));
            });
            copyBtn.sizing(Sizing.fixed(46), Sizing.fixed(14));
            copyBtn.tooltip(Component.literal("Copy coordinates to clipboard"));
            headerRow.child(copyBtn);

            container.child(headerRow);
        } else {
            container.child(UIComponents.label(
                    Component.literal(row.label()).withStyle(ChatFormatting.GRAY))
                    .maxWidth(textMaxWidth));
        }

        container.child(UIComponents.label(
                Component.literal(row.value()).withStyle(ChatFormatting.WHITE))
                .maxWidth(textMaxWidth));

        return container;
    }

    private static MetadataReadResult getCachedMetadataByPath(Path path) {
        if (path == null) {
            return null;
        }
        Path normalized = path.normalize();
        synchronized (METADATA_CACHE) {
            for (Map.Entry<MetadataCacheKey, MetadataReadResult> entry : METADATA_CACHE.entrySet()) {
                if (entry.getKey().path().equals(normalized)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static MetadataReadResult getOrReadMetadata(Path path) {
        if (path == null) {
            return MetadataReadResult.empty();
        }
        Path normalized = path.normalize();
        long lastModifiedMillis = -1L;
        try {
            BasicFileAttributes attrs = Files.readAttributes(normalized, BasicFileAttributes.class);
            if (!attrs.isRegularFile()) {
                evictMetadataCache(normalized);
                return MetadataReadResult.empty();
            }
            lastModifiedMillis = attrs.lastModifiedTime().toMillis();
        } catch (IOException ignored) {
            evictMetadataCache(normalized);
            return MetadataReadResult.empty();
        }

        MetadataCacheKey key = new MetadataCacheKey(normalized, lastModifiedMillis);
        synchronized (METADATA_CACHE) {
            MetadataReadResult cached = METADATA_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }

        MetadataReadResult readResult = ScreenshotMetadataReader.read(normalized);
        long resultModified = readResult.fileInfo().lastModifiedMillis();
        MetadataCacheKey storeKey = new MetadataCacheKey(
                normalized,
                resultModified > 0 ? resultModified : lastModifiedMillis);

        synchronized (METADATA_CACHE) {
            // Remove any older entries for this same path before storing the latest version
            Iterator<MetadataCacheKey> it = METADATA_CACHE.keySet().iterator();
            while (it.hasNext()) {
                MetadataCacheKey existing = it.next();
                if (existing.path().equals(normalized) && !existing.equals(storeKey)) {
                    it.remove();
                }
            }
            METADATA_CACHE.put(storeKey, readResult);
        }
        return readResult;
    }

    private static void evictMetadataCache(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.normalize();
        synchronized (METADATA_CACHE) {
            METADATA_CACHE.keySet().removeIf(k -> k.path().equals(normalized));
        }
    }

    static void clearMetadataCache() {
        synchronized (METADATA_CACHE) {
            METADATA_CACHE.clear();
        }
    }

    private void navigateBy(int delta) {
        if (deleting || images.isEmpty()) {
            return;
        }
        int nextIndex = GalleryNavigation.moveSingleImageIndex(currentIndex, delta, images.size());
        if (nextIndex >= 0 && nextIndex != currentIndex) {
            currentIndex = nextIndex;
            rebuild();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (this.uiAdapter == null) {
            return super.keyPressed(input);
        }

        var focusHandler = this.uiAdapter.rootComponent.focusHandler();
        UIComponent focused = focusHandler != null ? focusHandler.focused() : null;
        if (focused instanceof GreedyInputUIComponent || input.hasControlDown() || input.hasAltDown()) {
            return super.keyPressed(input);
        }

        if (input.key() == InputConstants.KEY_I) {
            clearComponentFocus();
            if (!deleting) {
                toggleMetadataPanel();
            }
            return true;
        }

        if (input.isLeft()) {
            clearComponentFocus();
            if (!deleting) {
                navigateBy(-1);
            }
            return true;
        }
        if (input.isRight()) {
            clearComponentFocus();
            if (!deleting) {
                navigateBy(1);
            }
            return true;
        }

        return super.keyPressed(input);
    }

    private void clearComponentFocus() {
        if (this.uiAdapter != null) {
            var focusHandler = this.uiAdapter.rootComponent.focusHandler();
            if (focusHandler != null) {
                focusHandler.focus(null, null);
            }
        }
    }

    private void clearDismountedFocus() {
        if (this.uiAdapter == null) {
            return;
        }
        var focusHandler = this.uiAdapter.rootComponent.focusHandler();
        if (focusHandler != null
                && focusHandler.focused() != null
                && focusHandler.focused().root() != this.uiAdapter.rootComponent) {
            focusHandler.focus(null, null);
        }
    }

    private void rebuild() {
        clearComponentFocus();
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
                evictMetadataCache(path);
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
            int returnIndex = images.isEmpty() ? -1 : this.currentIndex;
            ScreenUtils.setScreen(this.minecraft, new GalleryScreen(this.parent, this.galleryDir, returnIndex));
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
            clearMetadataCache();
        }
    }
}
