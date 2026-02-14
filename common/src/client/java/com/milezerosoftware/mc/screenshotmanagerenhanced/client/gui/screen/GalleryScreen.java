package com.milezerosoftware.mc.screenshotmanagerenhanced.client.gui.screen;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ScreenshotTextureManager;
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
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class GalleryScreen extends BaseOwoScreen<FlowLayout> {
    private Path currentDir;
    private final Path rootDir;
    private int columns = 3; // Default to 3x3 as requested

    public GalleryScreen(Path rootDir) {
        this.rootDir = rootDir;
        this.currentDir = rootDir;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    // Attempting strict case match with prompt's suggestion
    // If it overrides, good. If not, it's a helper.
    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.verticalAlignment(VerticalAlignment.CENTER);

        var main = Containers.verticalFlow(Sizing.fill(90), Sizing.fill(90));

        var topBar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        topBar.verticalAlignment(VerticalAlignment.CENTER);

        topBar.child(Components.button(Text.literal("↑"), b -> {
            if (!currentDir.equals(rootDir)) {
                currentDir = currentDir.getParent();
                refresh(root);
            }
        }).id("up-button").sizing(Sizing.fixed(20)));

        topBar.child(Components.label(Text.literal("screenshots/"))
                .id("path-label")
                .margins(Insets.left(10)));

        // Spacer
        topBar.child(Containers.horizontalFlow(Sizing.fill(50), Sizing.fixed(1)));

        // Grid Size Toggle
        topBar.child(Components.button(Text.literal(" " + columns + " Columns "), b -> {
            cycleGridSize(b);
            refresh(root);
        }).id("grid-toggle").margins(Insets.right(10)));

        main.child(topBar);

        var contentContainer = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(80));
        contentContainer.id("content-container");
        main.child(contentContainer);

        main.child(Components.button(Text.literal("Close"), b -> this.close())
                .id("close-button")
                .sizing(Sizing.fixed(100), Sizing.fixed(20))
                .margins(Insets.top(10)));

        root.child(main);

        refresh(root);
    }

    // Cycle: 1 -> 3 -> 6 -> 8 -> 1
    private void cycleGridSize(io.wispforest.owo.ui.component.ButtonComponent button) {
        if (columns == 1)
            columns = 3;
        else if (columns == 3)
            columns = 6;
        else if (columns == 6)
            columns = 8;
        else
            columns = 1;

        button.setMessage(Text.literal(" " + columns + " Columns "));
    }

    private void refresh(FlowLayout root) {
        var pathLabel = root.childById(LabelComponent.class, "path-label");
        var contentContainer = root.childById(FlowLayout.class, "content-container");

        if (pathLabel == null || contentContainer == null)
            return;

        pathLabel.text(Text.literal("Folder: " + rootDir.relativize(currentDir)));
        contentContainer.clearChildren();

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

            // Container for scrolling content
            var scrollContent = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            scrollContent.gap(10);

            // 1. Folders Section
            if (!dirs.isEmpty()) {
                var folderFlow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                folderFlow.gap(5);
                folderFlow.margins(Insets.of(5));
                // Allow wrapping? horizontalFlow wraps if using specific settings or we use
                // flexible?
                // Owo-ui flow layouts don't wrap items automatically to next line unless
                // configured?
                // Actually they just overflow. We might need a flexible grid or wrap layout.
                // FlowLayout does NOT wrap by default.
                // Let's use a "grid" for folders too, or just a flowing set of buttons?
                // If we want wrapping chips, we need a layout that supports it.
                // Owo doesn't have a "FlowWrap" layout easily accessible?
                // Let's use a grid for folders, but dense.
                // Or just a dynamic grid based on width.

                // Let's use a grid for folders, 4 columns?
                int folderCols = 4;
                int folderRows = (int) Math.ceil((double) dirs.size() / folderCols);
                var folderGrid = Containers.grid(Sizing.fill(100), Sizing.content(), folderRows, folderCols);

                // Calculate folder cell width
                int totalWidth = (int) (this.width * 0.90) - 20;
                int fGap = 4;
                int fCellWidth = (totalWidth - (folderCols - 1) * fGap) / folderCols;

                for (int i = 0; i < dirs.size(); i++) {
                    folderGrid.child(createFolderUI(dirs.get(i), fCellWidth, 20), i / folderCols, i % folderCols);
                }
                scrollContent.child(folderGrid);
            }

            // 2. Images Section
            if (!images.isEmpty()) {
                int fileCount = images.size();
                int rows = (int) Math.ceil((double) fileCount / columns);
                if (rows < 1)
                    rows = 1;

                var grid = Containers.grid(Sizing.fill(100), Sizing.content(), rows, columns);
                grid.id("screenshot-grid");
                grid.horizontalAlignment(HorizontalAlignment.CENTER);

                int totalWidth = (int) (this.width * 0.90) - 20;
                int gap = 4;
                int cellWidth = (totalWidth - (columns - 1) * gap) / columns;
                int cellHeight = (int) (cellWidth * (9.0 / 16.0));

                int thumbWidth = 320;
                if (columns <= 1)
                    thumbWidth = 1280;
                else if (columns <= 3)
                    thumbWidth = 640;

                for (int i = 0; i < images.size(); i++) {
                    grid.child(createImageUI(images.get(i), cellWidth, cellHeight, thumbWidth), i / columns,
                            i % columns);
                }
                scrollContent.child(grid);
            }

            var scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), scrollContent);
            // scroll.scrollbarThru(true);
            contentContainer.child(scroll);

        } catch (IOException e) {
            e.printStackTrace();
            contentContainer.child(Components.label(Text.literal("Error loading files")));
        }
    }

    private Component createFolderUI(Path path, int w, int h) {
        String name = path.getFileName().toString();
        // Simple truncation
        if (name.length() > 15) {
            name = name.substring(0, 12) + "...";
        }

        return Components.button(Text.literal("📁 " + name), b -> {
            this.currentDir = path;
            refresh((FlowLayout) this.uiAdapter.rootComponent);
        }).sizing(Sizing.fixed(w), Sizing.fixed(20)) // Folders maybe just strips? Or boxes?
                .margins(Insets.of(2)) // Small margin for the cell
                .tooltip(Text.literal(path.getFileName().toString())); // Full name on hover
    }

    private Component createImageUI(Path path, int w, int h, int thumbWidth) {
        Identifier thumb = ScreenshotTextureManager.getOrCreateThumbnail(path, thumbWidth);
        int thumbHeight = (int) (thumbWidth * 9.0 / 16.0);

        var texture = Components.texture(thumb, 0, 0, thumbWidth, thumbHeight, thumbWidth, thumbHeight);
        texture.cursorStyle(CursorStyle.HAND);
        texture.sizing(Sizing.fixed(w), Sizing.fixed(h));
        texture.mouseDown().subscribe((cx, cy, button) -> {
            Util.getOperatingSystem().open(path.toUri());
            return true;
        });
        // Add margins to create the "padding" between grid items
        return texture.margins(Insets.of(2));
    }

    @Override
    public void removed() {
        super.removed();
        ScreenshotTextureManager.clearCache();
    }
}
