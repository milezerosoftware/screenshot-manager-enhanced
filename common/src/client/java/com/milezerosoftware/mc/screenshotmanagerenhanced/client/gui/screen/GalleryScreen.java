package com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ScreenshotTextureManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.GalleryDirectoryScanner;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.GalleryNavigation;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotPathGenerator;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.WorldUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
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
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import io.wispforest.owo.ui.util.FocusHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GalleryScreen extends BaseOwoScreen<FlowLayout> {
    private static final int MAX_UPLOADS_PER_FRAME = 4;
    private static final int SELECTION_BORDER_COLOR = 0xFFFFD700;
    private static final int SELECTION_FILL_COLOR = 0x24FFFFFF;
    private static final int SELECTION_BORDER_THICKNESS = 2;

    private Path currentDir;
    private final Path rootDir;
    private final Screen parent;
    private static int columns = 3; // Default to 3x3 as requested, static for session persistence

    private List<Path> currentDirs = List.of();
    private List<Path> currentImages = List.of();
    private int currentPage = 0;
    private int selectedIndex = -1;
    private final Map<Integer, UIComponent> pageCellComponents = new HashMap<>();
    private ScrollContainer<FlowLayout> activeScroll = null;
    private FlowLayout activeScrollContent = null;
    private double keyboardScrollTarget = 0.0;
    private boolean pendingScrollToSelected = false;
    private long refreshGeneration = 0;
    private long pageRenderGeneration = 0;
    private boolean navigatingToSingleView = false;

    public GalleryScreen(Screen parent) {
        this(parent, null, -1);
    }

    public GalleryScreen(Screen parent, Path currentDir) {
        this(parent, currentDir, -1);
    }

    public GalleryScreen(Screen parent, Path currentDir, int initialSelectedIndex) {
        this.parent = parent;
        Path defaultScreenshotsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        this.rootDir = GalleryDirectoryScanner.resolveRootDirectory(defaultScreenshotsDir, ConfigManager.getInstance());
        this.currentDir = (currentDir != null ? currentDir : resolveStartPath()).normalize();
        this.selectedIndex = initialSelectedIndex >= 0 ? initialSelectedIndex : -1;
        this.pendingScrollToSelected = this.selectedIndex >= 0;
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
            if (this.pendingScrollToSelected) {
                tryScrollSelectedIntoView();
            }
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
                selectedIndex = -1;
                pendingScrollToSelected = false;
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
            if (selectedIndex >= 0 && selectedIndex < currentImages.size()) {
                currentPage = GalleryNavigation.pageForIndex(selectedIndex, pageSize);
                pendingScrollToSelected = true;
            } else {
                currentPage = GalleryDirectoryScanner.clampPage(currentPage, currentImages.size(), pageSize);
            }
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
            navigatePageBy(root, -1);
        });
        prevPageBtn.id("prev-page-button");
        prevPageBtn.sizing(Sizing.fixed(50), Sizing.fixed(20));
        prevPageBtn.active = false;
        paginationBar.child(prevPageBtn);

        var pageLabel = UIComponents.label(Component.literal("Page 1 / 1 (0 images)"));
        pageLabel.id("page-label");
        paginationBar.child(pageLabel);

        var nextPageBtn = UIComponents.button(Component.literal("Next >"), b -> {
            navigatePageBy(root, 1);
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
        clearDismountedFocus();
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
                    || ScreenUtils.getCurrentScreen() != this) {
                return;
            }

            if (result == null) {
                contentContainer.clearChildren();
                clearDismountedFocus();
                contentContainer.child(UIComponents.label(Component.literal("Error loading files")));
                return;
            }

            this.currentDirs = result.directories();
            this.currentImages = result.imagePaths();
            int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
            this.selectedIndex = GalleryNavigation.clampSelection(this.selectedIndex, this.currentImages.size());
            if (this.selectedIndex >= 0) {
                this.currentPage = GalleryNavigation.pageForIndex(this.selectedIndex, pageSize);
                this.pendingScrollToSelected = true;
            } else {
                this.currentPage = GalleryDirectoryScanner.clampPage(this.currentPage, this.currentImages.size(), pageSize);
            }

            renderCurrentPage(root);
        }, Minecraft.getInstance()); // Execute on Main Thread
    }

    private void renderCurrentPage(FlowLayout root) {
        var contentContainer = root.childById(FlowLayout.class, "content-container");
        if (contentContainer == null)
            return;

        long renderGen = ++this.pageRenderGeneration;
        this.pageCellComponents.clear();
        this.activeScroll = null;
        this.activeScrollContent = null;
        this.keyboardScrollTarget = 0.0;

        int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
        int totalImages = currentImages.size();
        int totalPages = GalleryDirectoryScanner.getTotalPages(totalImages, pageSize);
        currentPage = GalleryDirectoryScanner.clampPage(currentPage, totalImages, pageSize);

        updatePaginationControls(root, totalPages, totalImages);

        contentContainer.clearChildren();
        clearDismountedFocus();

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
                this.pageCellComponents.put(globalIndex, cell);
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

        scrollContent.mouseScroll().subscribe((mouseX, mouseY, amount) -> {
            this.keyboardScrollTarget = -1.0;
            return false;
        });

        var scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.fill(100), scrollContent);
        scroll.id("gallery-scroll");
        scroll.mouseDown().subscribe((click, doubled) -> {
            this.keyboardScrollTarget = -1.0;
            return false;
        });
        this.activeScroll = scroll;
        this.activeScrollContent = scrollContent;
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
            this.selectedIndex = -1;
            this.pendingScrollToSelected = false;
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
                    || ScreenUtils.getCurrentScreen() != this) {
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

        // Click Layer (Invisible Button excluded from keyboard Tab cycle so only toolbar/folder buttons participate)
        var button = new ButtonComponent(Component.empty(), b -> {
            this.selectedIndex = index;
            openSingleImage(images, index);
        }) {
            @Override
            public boolean canFocus(FocusSource source) {
                return source != FocusSource.KEYBOARD_CYCLE;
            }
        };
        button.sizing(Sizing.fill(100), Sizing.fill(100));

        final boolean[] hovered = {false};
        button.mouseEnter().subscribe(() -> hovered[0] = true);
        button.mouseLeave().subscribe(() -> hovered[0] = false);

        // Transparent background with distinct selection highlight and hover outline
        button.renderer((context, btn, delta) -> {
            if (index == this.selectedIndex) {
                context.fill(btn.x(), btn.y(), btn.x() + btn.width(), btn.y() + btn.height(), SELECTION_FILL_COLOR);
                int border = SELECTION_BORDER_THICKNESS;
                context.fill(btn.x(), btn.y(), btn.x() + btn.width(), btn.y() + border, SELECTION_BORDER_COLOR); // Top
                context.fill(btn.x(), btn.y() + btn.height() - border, btn.x() + btn.width(), btn.y() + btn.height(), SELECTION_BORDER_COLOR); // Bottom
                context.fill(btn.x(), btn.y(), btn.x() + border, btn.y() + btn.height(), SELECTION_BORDER_COLOR); // Left
                context.fill(btn.x() + btn.width() - border, btn.y(), btn.x() + btn.width(), btn.y() + btn.height(), SELECTION_BORDER_COLOR); // Right
            } else if (hovered[0]) {
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

    private void openSingleImage(List<Path> images, int index) {
        if (images == null || index < 0 || index >= images.size()) {
            return;
        }
        this.navigatingToSingleView = true;
        ScreenUtils.setScreen(new SingleImageScreen(this.parent, images, index, this.currentDir));
    }

    private void navigatePageBy(FlowLayout root, int delta) {
        if (currentImages.isEmpty()) {
            return;
        }
        int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
        if (selectedIndex >= 0) {
            var move = GalleryNavigation.movePage(currentPage, selectedIndex, delta, currentImages.size(), pageSize);
            if (move.pageChanged() || move.selectedIndex() != selectedIndex) {
                applyGridMove(move);
            }
        } else {
            int targetPage = GalleryDirectoryScanner.clampPage(currentPage + delta, currentImages.size(), pageSize);
            if (targetPage != currentPage) {
                currentPage = targetPage;
                ScreenshotTextureManager.cancelPending();
                renderCurrentPage(root);
            }
        }
    }

    private void applyGridMove(GalleryNavigation.GridMoveResult move) {
        this.selectedIndex = move.selectedIndex();
        if (move.pageChanged()) {
            this.currentPage = move.page();
            ScreenshotTextureManager.cancelPending();
            this.pendingScrollToSelected = true;
            if (this.uiAdapter != null) {
                renderCurrentPage(this.uiAdapter.rootComponent);
            }
        } else {
            ensureSelectedVisible();
        }
    }

    private void ensureSelectedVisible() {
        this.pendingScrollToSelected = true;
        tryScrollSelectedIntoView();
    }

    private void tryScrollSelectedIntoView() {
        if (this.selectedIndex < 0 || this.activeScroll == null || this.activeScrollContent == null) {
            this.pendingScrollToSelected = false;
            return;
        }
        UIComponent cell = this.pageCellComponents.get(this.selectedIndex);
        if (cell == null) {
            this.pendingScrollToSelected = false;
            return;
        }
        if (cell.height() <= 0 || this.activeScroll.height() <= 0 || this.activeScrollContent.height() <= 0) {
            return; // Wait until layout pass assigns bounds
        }
        this.pendingScrollToSelected = false;

        Insets scrollPadding = this.activeScroll.padding().get();
        Insets contentMargins = this.activeScrollContent.margins().get();
        Insets cellMargins = cell.margins().get();

        int viewportHeight = this.activeScroll.height() - scrollPadding.vertical();
        int maxScroll = Math.max(0, this.activeScrollContent.height() - viewportHeight);
        if (maxScroll <= 0) {
            this.keyboardScrollTarget = 0.0;
            return;
        }

        int itemTop = cell.y() - this.activeScrollContent.y() - cellMargins.top();
        int itemBottom = itemTop + cell.height() + cellMargins.vertical();

        boolean hadKeyboardTarget = this.keyboardScrollTarget >= 0.0;
        double currentOffset = hadKeyboardTarget
                ? this.keyboardScrollTarget
                : (this.activeScroll.y() + scrollPadding.top() + contentMargins.top()) - this.activeScrollContent.y();

        double targetOffset = GalleryNavigation.computeTargetScrollOffset(
                itemTop, itemBottom, currentOffset, viewportHeight, maxScroll);
        if (Math.abs(targetOffset - currentOffset) > 1e-6) {
            this.keyboardScrollTarget = targetOffset;
            double progress = Math.max(0.0, Math.min(1.0, targetOffset / maxScroll));
            this.activeScroll.scrollTo(progress);
        } else if (hadKeyboardTarget) {
            this.keyboardScrollTarget = targetOffset;
        }
    }

    private void clearDismountedFocus() {
        if (this.uiAdapter == null) {
            return;
        }
        FocusHandler focusHandler = this.uiAdapter.rootComponent.focusHandler();
        if (focusHandler != null && focusHandler.focused() != null && !focusHandler.focused().hasParent()) {
            focusHandler.focus(null, null);
        }
    }

    private boolean isNonImageButtonKeyboardFocused(FocusHandler focusHandler) {
        if (focusHandler == null) {
            return false;
        }
        return focusHandler.focused() instanceof ButtonComponent btn
                && btn.active
                && btn.hasParent()
                && focusHandler.lastFocusSource() == UIComponent.FocusSource.KEYBOARD_CYCLE;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        this.keyboardScrollTarget = -1.0;
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (this.uiAdapter == null) {
            return super.keyPressed(input);
        }

        FocusHandler focusHandler = this.uiAdapter.rootComponent.focusHandler();
        UIComponent focused = focusHandler != null ? focusHandler.focused() : null;
        if (focused instanceof GreedyInputUIComponent || input.hasControlDown() || input.hasAltDown()) {
            return super.keyPressed(input);
        }

        GalleryNavigation.Direction direction = null;
        if (input.isLeft()) {
            direction = GalleryNavigation.Direction.LEFT;
        } else if (input.isRight()) {
            direction = GalleryNavigation.Direction.RIGHT;
        } else if (input.isUp()) {
            direction = GalleryNavigation.Direction.UP;
        } else if (input.isDown()) {
            direction = GalleryNavigation.Direction.DOWN;
        }

        if (direction != null) {
            if (focusHandler != null) {
                focusHandler.focus(null, null);
            }
            if (!currentImages.isEmpty()) {
                int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
                var move = GalleryNavigation.moveGridSelection(
                        selectedIndex, currentPage, direction, currentImages.size(), columns, pageSize);
                applyGridMove(move);
            }
            return true;
        }

        int keyCode = input.key();
        if (keyCode == InputConstants.KEY_PAGEUP || keyCode == InputConstants.KEY_PAGEDOWN) {
            if (focusHandler != null) {
                focusHandler.focus(null, null);
            }
            if (!currentImages.isEmpty()) {
                int delta = keyCode == InputConstants.KEY_PAGEUP ? -1 : 1;
                int pageSize = GalleryDirectoryScanner.pageSizeForColumns(columns);
                var move = GalleryNavigation.movePage(
                        currentPage, selectedIndex, delta, currentImages.size(), pageSize);
                applyGridMove(move);
            }
            return true;
        }

        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                && !isNonImageButtonKeyboardFocused(focusHandler)
                && selectedIndex >= 0
                && selectedIndex < currentImages.size()) {
            openSingleImage(currentImages, selectedIndex);
            return true;
        }

        return super.keyPressed(input);
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
        this.pageCellComponents.clear();
        this.activeScroll = null;
        this.activeScrollContent = null;
        this.keyboardScrollTarget = 0.0;
        this.pendingScrollToSelected = false;
        this.uiAdapter = null; // Force rebuild on next init to reload textures
        if (this.navigatingToSingleView) {
            this.navigatingToSingleView = false;
            ScreenshotTextureManager.cancelPending();
        } else {
            ScreenshotTextureManager.clearCache();
            SingleImageScreen.clearMetadataCache();
        }
    }
}
