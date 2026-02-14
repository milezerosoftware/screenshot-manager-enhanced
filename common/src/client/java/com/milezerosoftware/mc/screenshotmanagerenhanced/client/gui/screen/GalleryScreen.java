package com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ScreenshotTextureManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotPathGenerator;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.WorldUtils;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ConfigManager;
import com.milezerosoftware.mc.screenshotmanagerenhanced.config.ModConfig;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

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
        this.rootDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");
        this.currentDir = resolveStartPath();
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        super.render(context, mouseX, mouseY, delta);
    }

    public GalleryScreen(Screen parent, Path currentDir) {
        this.parent = parent;
        this.rootDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");
        this.currentDir = currentDir != null ? currentDir : resolveStartPath();
    }

    private Path resolveStartPath() {
        ModConfig config = ConfigManager.getInstance();
        File gameDir = MinecraftClient.getInstance().runDirectory;
        File screenshotsDir = new File(gameDir, "screenshots");

        if (MinecraftClient.getInstance().world == null) {
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
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.verticalAlignment(VerticalAlignment.CENTER);

        // Full screen main container to ensure edge alignment works
        var main = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        main.verticalAlignment(VerticalAlignment.TOP);
        // Add vertical padding to main to avoid hugging screen top/bottom too tightly
        // if needed
        // But requested to match SingleView which seems to flush? We'll keep it simple.

        // Top Bar
        var topBar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        topBar.verticalAlignment(VerticalAlignment.CENTER);
        topBar.margins(Insets.top(5).withBottom(5)); // Small top margin
        topBar.padding(Insets.horizontal(20)); // Match SingleImageScreen padding

        // Left Group (Up + Label) - Pinned Left
        var topLeft = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        topLeft.verticalAlignment(VerticalAlignment.CENTER);
        topLeft.child(Components.button(Text.literal("↑"), b -> {
            Path parentPath = currentDir.getParent();
            if (parentPath != null && (parentPath.startsWith(rootDir) || parentPath.equals(rootDir))) {
                currentDir = parentPath;
                refresh(root);
            }
        }).id("up-button").sizing(Sizing.fixed(20)));

        topLeft.child(Components.label(Text.literal("screenshots/"))
                .id("path-label")
                .margins(Insets.left(10)));

        // Use relative positioning to pin left
        topLeft.positioning(Positioning.relative(0, 50));
        topBar.child(topLeft);

        // Right Group (Columns) - Pinned Right
        var topRight = Components.button(Text.literal(" " + columns + " Columns "), b -> {
            cycleGridSize(b);
            refresh(root);
        }).id("grid-toggle");
        topRight.margins(Insets.right(0));

        // Use relative positioning to pin right
        topRight.positioning(Positioning.relative(100, 20));
        topBar.child(topRight);

        main.child(topBar);

        // Content Container (Grid)
        // Reduce height to safe value to prevent bottom bar overflow (e.g., 80%)
        // With main=100%, top=30px, bottom=30px. 80% leaves 20% (~something px).
        var contentContainer = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(80));
        contentContainer.id("content-container");
        // Add margin if grid feels too wide (optional, but consistent with old 90%
        // main)
        contentContainer.margins(Insets.horizontal(5));
        main.child(contentContainer);

        // Bottom Bar
        var bottomBar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30)); // Fixed height for consistency
        bottomBar.verticalAlignment(VerticalAlignment.CENTER);
        bottomBar.margins(Insets.top(10));
        bottomBar.padding(Insets.horizontal(20)); // Match SingleImageScreen padding

        // Back Button (Left) - Pinned Left
        var backBtn = Components.button(Text.literal("Back"), b -> {
            this.close();
        }).sizing(Sizing.fixed(90), Sizing.fixed(20));
        backBtn.positioning(Positioning.relative(0, 50));
        bottomBar.child(backBtn);

        // Open Folder Button (Right) - Pinned Right
        var openFolderBtn = Components.button(Text.literal("Open Folder"), b -> {
            Util.getOperatingSystem().open(currentDir.toFile());
        }).sizing(Sizing.fixed(90), Sizing.fixed(20));
        openFolderBtn.positioning(Positioning.relative(100, 20));
        bottomBar.child(openFolderBtn);

        main.child(bottomBar);

        root.child(main);

        refresh(root);
    }

    // ... (cycleGridSize and refresh methods remain unchanged, assuming update
    // handles block correctly)

    // ...

    private void cycleGridSize(io.wispforest.owo.ui.component.ButtonComponent button) {
        if (columns == 3)
            columns = 6;
        else if (columns == 6)
            columns = 8;
        else
            columns = 3;

        button.setMessage(Text.literal(" " + columns + " Columns "));
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

        pathLabel.text(Text.literal("Folder: " + displayPath));
        contentContainer.clearChildren();
        contentContainer.child(Components.label(Text.literal("Loading...")));

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
                contentContainer.child(Components.label(Text.literal("Error loading files")));
                return;
            }

            var dirs = result.left();
            var images = result.right();

            contentContainer.clearChildren();

            var scrollContent = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            scrollContent.gap(0); // Gap handled by margins of children now
            scrollContent.padding(Insets.of(3)); // Outer padding

            // Grid Layout Constants
            int spacing = 6;
            int halfSpacing = spacing / 2;
            int scrollBarReserve = 20; // Safe space for scrollbar
            // Available width is screen width minus margins/scrollbar
            // Using this.width because main container fills screen
            int availableWidth = this.width - scrollBarReserve - 10;

            // Width available for ACTUAL CELLS (excluding gaps)
            // Total Spacing = columns * spacing (since we use half-spacing on all sides of
            // every cell)
            int widthForCells = availableWidth - (columns * spacing);
            int cellWidth = widthForCells / columns;

            // Limit minimum size
            if (cellWidth < 50)
                cellWidth = 50;

            if (!dirs.isEmpty()) {
                // Use grid layout matching image columns
                int rows = (int) Math.ceil((double) dirs.size() / columns);
                var folderGrid = Containers.grid(Sizing.fill(100), Sizing.content(), rows, columns);
                folderGrid.horizontalAlignment(HorizontalAlignment.CENTER);

                for (int i = 0; i < dirs.size(); i++) {
                    var cell = createFolderUI(dirs.get(i), cellWidth);
                    cell.margins(Insets.of(halfSpacing)); // Even spacing
                    folderGrid.child(cell, i / columns, i % columns);
                }

                // Fill remaining slots
                int filledSlots = dirs.size();
                int totalSlots = rows * columns;
                for (int i = filledSlots; i < totalSlots; i++) {
                    var filler = Containers.verticalFlow(Sizing.fixed(cellWidth), Sizing.fixed(20));
                    filler.margins(Insets.of(halfSpacing));
                    folderGrid.child(filler, i / columns, i % columns);
                }

                scrollContent.child(folderGrid);
            }

            if (!images.isEmpty()) {
                int fileCount = images.size();
                int rows = (int) Math.ceil((double) fileCount / columns);

                var grid = Containers.grid(Sizing.fill(100), Sizing.content(), rows, columns);
                grid.id("screenshot-grid");
                grid.horizontalAlignment(HorizontalAlignment.CENTER);

                int cellHeight = (int) (cellWidth * (9.0 / 16.0));
                if (cellHeight < 50)
                    cellHeight = 50;

                for (int i = 0; i < images.size(); i++) {
                    var cell = createImageUI(images, i, columns, cellWidth, cellHeight);
                    cell.margins(Insets.of(halfSpacing)); // Even spacing
                    grid.child(cell, i / columns, i % columns);
                }

                // Fill remaining slots
                int filledSlots = images.size();
                int totalSlots = rows * columns;
                for (int i = filledSlots; i < totalSlots; i++) {
                    var filler = Containers.verticalFlow(Sizing.fixed(cellWidth), Sizing.fixed(cellHeight));
                    filler.margins(Insets.of(halfSpacing));
                    grid.child(filler, i / columns, i % columns);
                }

                scrollContent.child(grid);
            }

            var scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), scrollContent);
            contentContainer.child(scroll);

        }, MinecraftClient.getInstance()); // Execute on Main Thread
    }

    private Component createFolderUI(Path path, int width) {
        String name = path.getFileName().toString();

        // Adjust truncation based on column count or width
        int maxLen = 25;
        if (columns >= 6)
            maxLen = 12;
        if (columns >= 8)
            maxLen = 8;

        if (name.length() > maxLen) {
            name = name.substring(0, maxLen - 3) + "...";
        }

        return Components.button(Text.literal("📁 " + name), b -> {
            this.currentDir = path;
            refresh((FlowLayout) this.uiAdapter.rootComponent);
        }).sizing(Sizing.fixed(width), Sizing.fixed(20))
                .margins(Insets.of(2))
                .tooltip(Text.literal(path.getFileName().toString()));
    }

    private Component createImageUI(java.util.List<Path> images, int index, int currentColumns, int w, int h) {
        Path path = images.get(index);

        int thumbBase = 320;
        if (currentColumns <= 3)
            thumbBase = 480;

        Identifier thumb = ScreenshotTextureManager.getOrCreateThumbnail(path, thumbBase);

        var wrapper = Containers.verticalFlow(Sizing.content(), Sizing.content());
        wrapper.cursorStyle(CursorStyle.HAND);

        int aspectW = 16;
        int aspectH = 9;
        var texture = Components.texture(thumb, 0, 0, aspectW, aspectH, aspectW, aspectH);
        texture.sizing(Sizing.fixed(w), Sizing.fixed(h));

        // Padding ensures the border is drawn outside the image content
        wrapper.padding(Insets.of(1));

        wrapper.child(texture);

        // Use dynamic surface for consistent hover effect instead of event listeners
        wrapper.surface((context, component) -> {
            // Use captured mouse coordinates from the render pass to ensure
            // exact synchronization with Owo-UI's internal logic (tooltips, etc.)
            if (component.isInBoundingBox(this.lastMouseX, this.lastMouseY)) {
                Surface.outline(0xFFFFFFFF).draw(context, component);
            }
        });

        wrapper.mouseDown().subscribe((mx, my, button) -> {
            if (button == 0) {
                MinecraftClient.getInstance()
                        .setScreen(new SingleImageScreen(this.parent, images, index, this.currentDir));
                return true;
            }
            return false;
        });

        String fileName = path.getFileName().toString();
        // Date removed as per user request

        wrapper.tooltip(Text.literal(fileName));

        wrapper.margins(Insets.of(2));

        return wrapper;
    }

    @Override
    public void close() {
        if (parent != null) {
            this.client.setScreen(parent);
        } else {
            super.close();
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
