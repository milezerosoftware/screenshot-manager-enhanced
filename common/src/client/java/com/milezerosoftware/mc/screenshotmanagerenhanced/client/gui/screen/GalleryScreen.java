package com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ScreenshotTextureManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.GalleryDirectoryScanner;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotPathGenerator;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.WorldUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GalleryScreen extends BaseOwoScreen<FlowLayout> {
    private static final int MAX_UPLOADS_PER_FRAME = 4;

    private Path currentDir;
    private final Path rootDir;
    private final Screen parent;
    private static int columns = 3; // Default to 3x3 as requested, static for session persistence

    private List<Path> currentDirs = List.of();
    private List<Path> currentImages = List.of();
    private int currentPage = 0;
    private long refreshGeneration = 0;
    private long pageRenderGeneration = 0;
    private boolean navigatingToSingleView = false;

    public GalleryScreen(Screen parent) {
        this(parent, null);
    }

    public GalleryScreen(Screen parent, Path currentDir) {
        this.parent = parent;
        Path defaultScreenshotsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        this.rootDir = GalleryDirectoryScanner.resolveRootDirectory(defaultScreenshotsDir, ConfigManager.getInstance());
        this.currentDir = (currentDir != null ? currentDir : resolveStartPath()).normalize();
    }

    private Path resolveStartPath() {
        ModConfig config = ConfigManager.getInstance();
        File gameDir = Minecraft.getInstance().gameDirectory;
        File screenshotsDir = new File(gameDir, "screenshots");

        if (Minecraft.getInstance().level == null) {
            return ScreenshotPathGenerator.getScreenshotDirectory(screenshotsDir, config, null, "", "", null).toPath();
        }

        String rawWorldId = WorldUtils.getWorldId();
        String safeWorldId = WorldUtils.sanitize(rawWorldId);
        String dimension = WorldUtils.getDimension();

        return ScreenshotPathGenerator
                .getScreenshotDirectory(screenshotsDir, config, rawWorldId, safeWorldId, dimension, null).toPath();
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        // Drain up to MAX_UPLOADS_PER_FRAME decoded thumbnails on the render thread each frame.
        // Using owo-lib's Surface callback keeps this cross-version compatible across MC 1.21.x and 26.x.
        root.surface((context, component) -> {
            ScreenshotTextureManager.drainPendingUploads(MAX_UPLOADS_PER_FRAME);
            Surface.VANILLA_TRANSLUCENT.draw(context, component);
        });
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.verticalAlignment(VerticalAlignment.CENTER);

        // Full screen main container to ensure edge alignment works
        var main = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        main.verticalAlignment(VerticalAlignment.TOP);

        // Top Bar
        var topBar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        topBar.verticalAlignment(VerticalAlignment.CENTER);
        topBar.margins(Insets.top(5).withBottom(5));
        topBar.padding(Insets.horizontal(20));

        // Left Group (Up + Label) - Pinned Left
        var topLeft = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        topLeft.verticalAlignment(VerticalAlignment.CENTER);
        topLeft.child(UIComponents.button(Component.literal("↑"), b -> {
            Path parentPath = currentDir.normalize().getParent();
            if (parentPath != null && parentPath.startsWith(rootDir)) {
                currentDir = parentPath;
                currentPage = 0;
                refresh(root);
            }
        }).id("up-button").sizing(Sizing.fixed(20)));

        topLeft.child(UIComponents.label(Component.literal("screenshots/"))
                .id("path-label")
                .margins(Insets.left(10)));

        // Use relative positioning to pin left
        topLeft.positioning(Positioning.relative(0, 50));
        topBar.child(topLeft);

        // Right Group (Columns) - Pinned Right
        var topRight = UIComponents.button(Component.literal(" " + columns + " Columns "), b -> {
            cycleGridSize(b);
            ScreenshotTextureManager.cancelPending();
            int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
            currentPage = GalleryDirectoryScanner.clampPage(currentPage, currentImages.size(), pageSize);
            renderCurrentPage(root);
        }).id("grid-toggle");
        topRight.margins(Insets.right(0));

        // Use relative positioning to pin right
        topRight.positioning(Positioning.relative(100, 20));
        topBar.child(topRight);

        main.child(topBar);

        // Content Container (Grid)
        var contentContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(80));
        contentContainer.id("content-container");
        contentContainer.margins(Insets.horizontal(5));
        main.child(contentContainer);

        // Bottom Bar
        var bottomBar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        bottomBar.verticalAlignment(VerticalAlignment.CENTER);
        bottomBar.margins(Insets.top(10));
        bottomBar.padding(Insets.horizontal(20));

        // Back Button (Left) - Pinned Left
        var backBtn = UIComponents.button(Component.literal("Back"), b -> {
            this.onClose();
        }).sizing(Sizing.fixed(90), Sizing.fixed(20));
        backBtn.positioning(Positioning.relative(0, 50));
        bottomBar.child(backBtn);

        // Pagination Controls (Center) - Pinned Center
        var paginationBar = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        paginationBar.verticalAlignment(VerticalAlignment.CENTER);
        paginationBar.gap(6);

        var prevPageBtn = UIComponents.button(Component.literal("< Prev"), b -> {
            if (currentPage > 0) {
                currentPage--;
                ScreenshotTextureManager.cancelPending();
                renderCurrentPage(root);
            }
        });
        prevPageBtn.id("prev-page-button");
        prevPageBtn.sizing(Sizing.fixed(50), Sizing.fixed(20));
        prevPageBtn.active = false;
        paginationBar.child(prevPageBtn);

        var pageLabel = UIComponents.label(Component.literal("Page 1 / 1 (0 images)"));
        pageLabel.id("page-label");
        paginationBar.child(pageLabel);

        var nextPageBtn = UIComponents.button(Component.literal("Next >"), b -> {
            int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
            int totalPages = GalleryDirectoryScanner.getTotalPages(currentImages.size(), pageSize);
            if (currentPage < totalPages - 1) {
                currentPage++;
                ScreenshotTextureManager.cancelPending();
                renderCurrentPage(root);
            }
        });
        nextPageBtn.id("next-page-button");
        nextPageBtn.sizing(Sizing.fixed(50), Sizing.fixed(20));
        nextPageBtn.active = false;
        paginationBar.child(nextPageBtn);

        paginationBar.positioning(Positioning.relative(50, 50));
        bottomBar.child(paginationBar);

        // Open Folder Button (Right) - Pinned Right
        var openFolderBtn = UIComponents.button(Component.literal("Open Folder"), b -> {
            Util.getPlatform().openPath(currentDir);
        }).sizing(Sizing.fixed(90), Sizing.fixed(20));
        openFolderBtn.positioning(Positioning.relative(100, 20));
        bottomBar.child(openFolderBtn);

        main.child(bottomBar);

        root.child(main);

        refresh(root);
    }

    private void cycleGridSize(ButtonComponent button) {
        if (columns == 3)
            columns = 6;
        else if (columns == 6)
            columns = 8;
        else
            columns = 3;

        button.setMessage(Component.literal(" " + columns + " Columns "));
    }

    private void refresh(FlowLayout root) {
        var pathLabel = root.childById(LabelComponent.class, "path-label");
        var contentContainer = root.childById(FlowLayout.class, "content-container");

        if (pathLabel == null || contentContainer == null)
            return;

        if (!Files.exists(currentDir)) {
            try {
                Files.createDirectories(currentDir);
            } catch (IOException e) {
                currentDir = rootDir;
            }
        }

        currentDir = currentDir.normalize();

        String displayPath;
        try {
            displayPath = rootDir.relativize(currentDir).toString();
            if (displayPath.isEmpty())
                displayPath = ".";
        } catch (IllegalArgumentException e) {
            displayPath = currentDir.toString();
        }

        pathLabel.text(Component.literal("Folder: " + displayPath));
        contentContainer.clearChildren();
        contentContainer.child(UIComponents.label(Component.literal("Loading...")));

        long generation = ++this.refreshGeneration;
        this.pageRenderGeneration++;
        ScreenshotTextureManager.cancelPending();

        Path dirToScan = this.currentDir;
        CompletableFuture.supplyAsync(() -> {
            try {
                return GalleryDirectoryScanner.scan(dirToScan);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }).thenAcceptAsync(result -> {
            if (generation != this.refreshGeneration
                    || this.uiAdapter == null
                    || Minecraft.getInstance().screen != this) {
                return;
            }

            if (result == null) {
                contentContainer.clearChildren();
                contentContainer.child(UIComponents.label(Component.literal("Error loading files")));
                return;
            }

            this.currentDirs = result.directories();
            this.currentImages = result.imagePaths();
            int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
            this.currentPage = GalleryDirectoryScanner.clampPage(this.currentPage, this.currentImages.size(), pageSize);

            renderCurrentPage(root);
        }, Minecraft.getInstance()); // Execute on Main Thread
    }

    private void renderCurrentPage(FlowLayout root) {
        var contentContainer = root.childById(FlowLayout.class, "content-container");
        if (contentContainer == null)
            return;

        long renderGen = ++this.pageRenderGeneration;

        int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
        int totalImages = currentImages.size();
        int totalPages = GalleryDirectoryScanner.getTotalPages(totalImages, pageSize);
        currentPage = GalleryDirectoryScanner.clampPage(currentPage, totalImages, pageSize);

        updatePaginationControls(root, totalPages, totalImages);

        contentContainer.clearChildren();

        var scrollContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        scrollContent.gap(0);
        scrollContent.padding(Insets.of(3));

        // Grid Layout Constants
        int spacing = 6;
        int halfSpacing = spacing / 2;
        int scrollBarReserve = 20;
        int availableWidth = this.width - scrollBarReserve - 10;

        int widthForCells = availableWidth - (columns * spacing);
        int cellWidth = widthForCells / columns;

        if (cellWidth < 50)
            cellWidth = 50;

        if (!currentDirs.isEmpty() && currentPage == 0) {
            int rows = (int) Math.ceil((double) currentDirs.size() / columns);
            var folderGrid = UIContainers.grid(Sizing.fill(100), Sizing.content(), rows, columns);
            folderGrid.horizontalAlignment(HorizontalAlignment.CENTER);

            for (int i = 0; i < currentDirs.size(); i++) {
                var cell = createFolderUI(currentDirs.get(i), cellWidth);
                cell.margins(Insets.of(halfSpacing));
                folderGrid.child(cell, i / columns, i % columns);
            }

            int filledSlots = currentDirs.size();
            int totalSlots = rows * columns;
            for (int i = filledSlots; i < totalSlots; i++) {
                var filler = UIContainers.verticalFlow(Sizing.fixed(cellWidth), Sizing.fixed(20));
                filler.margins(Insets.of(halfSpacing));
                folderGrid.child(filler, i / columns, i % columns);
            }

            scrollContent.child(folderGrid);
        }

        List<Path> pageImages = GalleryDirectoryScanner.getPageSlice(currentImages, currentPage, pageSize);
        if (!pageImages.isEmpty()) {
            int fileCount = pageImages.size();
            int rows = (int) Math.ceil((double) fileCount / columns);

            var grid = UIContainers.grid(Sizing.fill(100), Sizing.content(), rows, columns);
            grid.id("screenshot-grid");
            grid.horizontalAlignment(HorizontalAlignment.CENTER);

            int cellHeight = (int) (cellWidth * (9.0 / 16.0));
            if (cellHeight < 50)
                cellHeight = 50;

            int pageStartIndex = currentPage * pageSize;
            for (int i = 0; i < pageImages.size(); i++) {
                int globalIndex = pageStartIndex + i;
                var cell = createImageUI(currentImages, globalIndex, columns, cellWidth, cellHeight, renderGen);
                cell.margins(Insets.of(halfSpacing));
                grid.child(cell, i / columns, i % columns);
            }

            int filledSlots = pageImages.size();
            int totalSlots = rows * columns;
            for (int i = filledSlots; i < totalSlots; i++) {
                var filler = UIContainers.verticalFlow(Sizing.fixed(cellWidth), Sizing.fixed(cellHeight));
                filler.margins(Insets.of(halfSpacing));
                grid.child(filler, i / columns, i % columns);
            }

            scrollContent.child(grid);
        }

        var scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.fill(100), scrollContent);
        contentContainer.child(scroll);
    }

    private void updatePaginationControls(FlowLayout root, int totalPages, int totalImages) {
        var prevBtn = root.childById(ButtonComponent.class, "prev-page-button");
        var nextBtn = root.childById(ButtonComponent.class, "next-page-button");
        var pageLabel = root.childById(LabelComponent.class, "page-label");

        if (prevBtn != null) {
            prevBtn.active = currentPage > 0;
        }
        if (nextBtn != null) {
            nextBtn.active = currentPage < totalPages - 1;
        }
        if (pageLabel != null) {
            pageLabel.text(Component.literal(
                    "Page " + (currentPage + 1) + " / " + totalPages + " (" + totalImages + " images)"));
        }
    }

    private UIComponent createFolderUI(Path path, int width) {
        String name = path.getFileName().toString();

        int maxLen = 25;
        if (columns >= 6)
            maxLen = 12;
        if (columns >= 8)
            maxLen = 8;

        if (name.length() > maxLen) {
            name = name.substring(0, maxLen - 3) + "...";
        }

        return UIComponents.button(Component.literal("\uD83D\uDCC1 " + name), b -> {
            this.currentDir = path;
            this.currentPage = 0;
            refresh((FlowLayout) this.uiAdapter.rootComponent);
        }).sizing(Sizing.fixed(width), Sizing.fixed(20))
                .margins(Insets.of(2))
                .tooltip(Component.literal(path.getFileName().toString()));
    }

    private UIComponent createImageUI(
            List<Path> images,
            int index,
            int currentColumns,
            int w,
            int h,
            long renderGen) {
        Path path = images.get(index);

        int thumbBase = 320;
        if (currentColumns <= 3)
            thumbBase = 480;

        // Content Layer
        var content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.padding(Insets.of(1));

        // Thumbnail container centered within cellW x cellH so non-16:9 images letterbox cleanly
        var thumbBox = UIContainers.verticalFlow(Sizing.fixed(w), Sizing.fixed(h));
        thumbBox.horizontalAlignment(HorizontalAlignment.CENTER);
        thumbBox.verticalAlignment(VerticalAlignment.CENTER);

        Identifier placeholderId = ScreenshotTextureManager.getPlaceholderId();
        var placeholderTexture = UIComponents.texture(placeholderId, 0, 0, 1, 1, 1, 1);
        placeholderTexture.sizing(Sizing.fixed(w), Sizing.fixed(h));
        thumbBox.child(placeholderTexture);
        content.child(thumbBox);

        ScreenshotTextureManager.requestThumbnail(path, thumbBase, result -> {
            if (renderGen != this.pageRenderGeneration
                    || this.uiAdapter == null
                    || Minecraft.getInstance().screen != this) {
                return;
            }
            int[] fitted = GalleryDirectoryScanner.fitWithinBounds(result.width(), result.height(), w, h);
            var loadedTexture = UIComponents.texture(
                    result.id(), 0, 0, result.width(), result.height(), result.width(), result.height());
            loadedTexture.sizing(Sizing.fixed(fitted[0]), Sizing.fixed(fitted[1]));
            thumbBox.clearChildren();
            thumbBox.child(loadedTexture);
        });

        // Label
        String fileName = path.getFileName().toString();
        if (fileName.length() > 20 && currentColumns > 4) {
            fileName = fileName.substring(0, 17) + "...";
        }
        var label = UIComponents.label(Component.literal(fileName));
        label.horizontalTextAlignment(HorizontalAlignment.CENTER);
        label.sizing(Sizing.fill(100), Sizing.content());
        label.margins(Insets.top(2));
        content.child(label);

        // Click Layer (Invisible Button)
        var button = UIComponents.button(Component.empty(), b -> {
            this.navigatingToSingleView = true;
            ScreenUtils.setScreen(new SingleImageScreen(this.parent, images, index, this.currentDir));
        });
        button.sizing(Sizing.fill(100), Sizing.fill(100));

        final boolean[] hovered = {false};
        button.mouseEnter().subscribe(() -> hovered[0] = true);
        button.mouseLeave().subscribe(() -> hovered[0] = false);

        // Transparent background
        button.renderer((context, btn, delta) -> {
            // Draw outline if hovered
            if (hovered[0]) {
                int color = 0xFFFFFFFF;
                context.fill(btn.x(), btn.y(), btn.x() + btn.width(), btn.y() + 1, color); // Top
                context.fill(btn.x(), btn.y() + btn.height() - 1, btn.x() + btn.width(), btn.y() + btn.height(), color); // Bottom
                context.fill(btn.x(), btn.y(), btn.x() + 1, btn.y() + btn.height(), color); // Left
                context.fill(btn.x() + btn.width() - 1, btn.y(), btn.x() + btn.width(), btn.y() + btn.height(), color); // Right
            }
        });
        button.cursorStyle(CursorStyle.HAND);

        button.tooltip(Component.literal(path.getFileName().toString()));

        // Stack Layer
        int fontHeight = Minecraft.getInstance().font.lineHeight;
        int calculatedHeight = h + fontHeight + 4;

        var stack = UIContainers.stack(Sizing.fixed(w), Sizing.fixed(calculatedHeight));
        stack.child(content);
        stack.child(button);

        stack.margins(Insets.of(2));

        return stack;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            ScreenUtils.setScreen(this.minecraft, parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        super.removed();
        this.refreshGeneration++;
        this.pageRenderGeneration++;
        this.uiAdapter = null; // Force rebuild on next init to reload textures
        if (this.navigatingToSingleView) {
            this.navigatingToSingleView = false;
            ScreenshotTextureManager.cancelPending();
        } else {
            ScreenshotTextureManager.clearCache();
        }
    }
}
