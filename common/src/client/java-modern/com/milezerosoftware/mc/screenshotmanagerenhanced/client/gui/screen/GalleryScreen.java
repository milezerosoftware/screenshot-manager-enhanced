package com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ScreenshotTextureManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotPathGenerator;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.WorldUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import io.wispforest.owo.ui.base.BaseOwoScreen;
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
import net.minecraft.client.gui.GuiGraphics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class GalleryScreen extends BaseOwoScreen<FlowLayout> {
    private Path currentDir;
    private final Path rootDir;
    private final Screen parent;
    private static int columns = 3; // Default to 3x3 as requested, static for session persistence
    private int lastMouseX = 0;
    private int lastMouseY = 0;

    public GalleryScreen(Screen parent) {
        this.parent = parent;
        this.rootDir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        this.currentDir = resolveStartPath();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        super.render(context, mouseX, mouseY, delta);
    }

    public GalleryScreen(Screen parent, Path currentDir) {
        this.parent = parent;
        this.rootDir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        this.currentDir = currentDir != null ? currentDir : resolveStartPath();
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
        root.surface(Surface.VANILLA_TRANSLUCENT);
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
            Path parentPath = currentDir.getParent();
            if (parentPath != null && (parentPath.startsWith(rootDir) || parentPath.equals(rootDir))) {
                currentDir = parentPath;
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
            refresh(root);
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

    private void cycleGridSize(io.wispforest.owo.ui.component.ButtonComponent button) {
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

        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try (Stream<Path> stream = Files.list(currentDir)) {
                var allFiles = stream.toList();
                var dirs = new java.util.ArrayList<Path>();
                var images = new java.util.ArrayList<Path>();

                for (Path p : allFiles) {
                    if (Files.isDirectory(p))
                        dirs.add(p);
                    else if (p.toString().toLowerCase().endsWith(".png"))
                        images.add(p);
                }

                dirs.sort((p1, p2) -> p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString()));
                images.sort((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                    } catch (IOException e) {
                        return 0;
                    }
                });

                return new Pair<>(dirs, images);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }).thenAcceptAsync(result -> {
            if (result == null) {
                contentContainer.child(UIComponents.label(Component.literal("Error loading files")));
                return;
            }

            var dirs = result.left();
            var images = result.right();

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

            if (!dirs.isEmpty()) {
                int rows = (int) Math.ceil((double) dirs.size() / columns);
                var folderGrid = UIContainers.grid(Sizing.fill(100), Sizing.content(), rows, columns);
                folderGrid.horizontalAlignment(HorizontalAlignment.CENTER);

                for (int i = 0; i < dirs.size(); i++) {
                    var cell = createFolderUI(dirs.get(i), cellWidth);
                    cell.margins(Insets.of(halfSpacing));
                    folderGrid.child(cell, i / columns, i % columns);
                }

                int filledSlots = dirs.size();
                int totalSlots = rows * columns;
                for (int i = filledSlots; i < totalSlots; i++) {
                    var filler = UIContainers.verticalFlow(Sizing.fixed(cellWidth), Sizing.fixed(20));
                    filler.margins(Insets.of(halfSpacing));
                    folderGrid.child(filler, i / columns, i % columns);
                }

                scrollContent.child(folderGrid);
            }

            if (!images.isEmpty()) {
                int fileCount = images.size();
                int rows = (int) Math.ceil((double) fileCount / columns);

                var grid = UIContainers.grid(Sizing.fill(100), Sizing.content(), rows, columns);
                grid.id("screenshot-grid");
                grid.horizontalAlignment(HorizontalAlignment.CENTER);

                int cellHeight = (int) (cellWidth * (9.0 / 16.0));
                if (cellHeight < 50)
                    cellHeight = 50;

                for (int i = 0; i < images.size(); i++) {
                    var cell = createImageUI(images, i, columns, cellWidth, cellHeight);
                    cell.margins(Insets.of(halfSpacing));
                    grid.child(cell, i / columns, i % columns);
                }

                int filledSlots = images.size();
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

        }, Minecraft.getInstance()); // Execute on Main Thread
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
            refresh((FlowLayout) this.uiAdapter.rootComponent);
        }).sizing(Sizing.fixed(width), Sizing.fixed(20))
                .margins(Insets.of(2))
                .tooltip(Component.literal(path.getFileName().toString()));
    }

    private UIComponent createImageUI(java.util.List<Path> images, int index, int currentColumns, int w, int h) {
        Path path = images.get(index);

        int thumbBase = 320;
        if (currentColumns <= 3)
            thumbBase = 480;

        Identifier thumb = ScreenshotTextureManager.getOrCreateThumbnail(path, thumbBase);

        // Content Layer
        var content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.padding(Insets.of(1));

        int aspectW = 16;
        int aspectH = 9;
        var texture = UIComponents.texture(thumb, 0, 0, aspectW, aspectH, aspectW, aspectH);
        texture.sizing(Sizing.fixed(w), Sizing.fixed(h));
        content.child(texture);

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
            Minecraft.getInstance()
                    .setScreen(new SingleImageScreen(this.parent, images, index, this.currentDir));
        });
        button.sizing(Sizing.fill(100), Sizing.fill(100));

        // Transparent background
        button.renderer((context, btn, delta) -> {
            // Draw outline if hovered (using captured coordinates for sync)
            if (btn.isInBoundingBox(this.lastMouseX, this.lastMouseY)) {
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
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        super.removed();
        this.uiAdapter = null; // Force rebuild on next init to reload textures
        ScreenshotTextureManager.clearCache();
    }

    private record Pair<L, R>(L left, R right) {
    }
}
