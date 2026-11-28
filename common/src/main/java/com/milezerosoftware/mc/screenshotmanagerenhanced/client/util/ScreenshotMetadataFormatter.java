package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader.FileInfo;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader.ParsedMetadata;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java formatter and layout calculator for the SingleImageScreen metadata inspector panel.
 */
public final class ScreenshotMetadataFormatter {

    public static final int NARROW_SCREEN_THRESHOLD = 360;
    public static final int MIN_PANEL_WIDTH = 170;
    public static final int MAX_PANEL_WIDTH = 220;
    public static final int PANEL_GAP = 10;
    public static final int HORIZONTAL_MARGIN = 12;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private ScreenshotMetadataFormatter() {
    }

    /**
     * A single display row in the metadata inspector.
     *
     * @param label     Field title (e.g. "World", "Coordinates", "Resolution")
     * @param value     Formatted display value
     * @param copyValue Optional text copied to clipboard when the row's Copy button is clicked
     */
    public record MetadataRow(String label, String value, String copyValue) {
        public MetadataRow(String label, String value) {
            this(label, value, null);
        }

        public boolean isCopyable() {
            return copyValue != null && !copyValue.isBlank();
        }
    }

    /**
     * Responsive geometry for the single-image view with or without the inspector panel open.
     */
    public record InspectorLayout(
            boolean panelOpen,
            boolean overlayMode,
            int panelWidth,
            int leftPaneWidth,
            int maxImageWidth,
            int maxImageHeight) {
    }

    /**
     * Builds ordered display rows for embedded Minecraft metadata, omitting blank or "Unknown" values.
     */
    public static List<MetadataRow> buildMinecraftRows(ParsedMetadata metadata) {
        if (metadata == null) {
            return List.of();
        }

        List<MetadataRow> rows = new ArrayList<>();
        addRowIfMeaningful(rows, "World", metadata.worldName(), null);
        addRowIfMeaningful(rows, "Dimension", metadata.dimension(), null);
        addRowIfMeaningful(rows, "Biome", metadata.biome(), null);

        String coordsCopy = extractCoordinateCopyText(metadata.coordinates());
        if (coordsCopy != null) {
            rows.add(new MetadataRow("Coordinates", coordsCopy, coordsCopy));
        }

        addRowIfMeaningful(rows, "Difficulty", metadata.difficulty(), null);
        addRowIfMeaningful(rows, "Days Played", metadata.daysPlayed(), null);
        addRowIfMeaningful(rows, "World Age", metadata.worldAge(), null);
        addRowIfMeaningful(rows, "MC Version", metadata.minecraftVersion(), null);
        addRowIfMeaningful(rows, "Player", metadata.playerName(), null);
        addRowIfMeaningful(rows, "Game Mode", formatGameMode(metadata.gameMode()), null);

        return List.copyOf(rows);
    }

    /**
     * Builds ordered display rows for file-level metadata using the system default timezone.
     */
    public static List<MetadataRow> buildFileInfoRows(FileInfo fileInfo) {
        return buildFileInfoRows(fileInfo, ZoneId.systemDefault());
    }

    /**
     * Builds ordered display rows for file-level metadata using an explicit {@link ZoneId}.
     */
    public static List<MetadataRow> buildFileInfoRows(FileInfo fileInfo, ZoneId zoneId) {
        if (fileInfo == null) {
            return List.of();
        }

        List<MetadataRow> rows = new ArrayList<>();
        if (fileInfo.hasDimensions()) {
            rows.add(new MetadataRow("Resolution", fileInfo.imageWidth() + " \u00D7 " + fileInfo.imageHeight()));
        }
        if (fileInfo.fileSizeBytes() >= 0) {
            rows.add(new MetadataRow("File Size", formatFileSize(fileInfo.fileSizeBytes())));
        }
        if (fileInfo.lastModifiedMillis() > 0) {
            ZoneId effectiveZone = zoneId != null ? zoneId : ZoneId.systemDefault();
            String formattedTime = formatTimestamp(fileInfo.lastModifiedMillis(), effectiveZone);
            if (formattedTime != null) {
                rows.add(new MetadataRow("Modified", formattedTime));
            }
        }

        return List.copyOf(rows);
    }

    /**
     * Extracts clipboard-ready coordinates text from {@link ParsedMetadata}, or {@code null} if unavailable.
     */
    public static String extractCoordinateCopyText(ParsedMetadata metadata) {
        return metadata == null ? null : extractCoordinateCopyText(metadata.coordinates());
    }

    /**
     * Extracts clipboard-ready coordinates text from a raw coordinate string, or {@code null} if blank/Unknown.
     */
    public static String extractCoordinateCopyText(String coordinates) {
        if (!isMeaningful(coordinates)) {
            return null;
        }
        return coordinates.trim();
    }

    /**
     * Formats a byte count into a human-readable string (e.g. {@code "512 B"}, {@code "24.5 KB"}, {@code "1.85 MB"}).
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "Unknown";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Formats an epoch-millisecond timestamp into {@code yyyy-MM-dd HH:mm} in the given timezone.
     */
    public static String formatTimestamp(long epochMillis, ZoneId zoneId) {
        if (epochMillis <= 0) {
            return null;
        }
        ZoneId effectiveZone = zoneId != null ? zoneId : ZoneId.systemDefault();
        return Instant.ofEpochMilli(epochMillis).atZone(effectiveZone).format(TIMESTAMP_FORMATTER);
    }

    /**
     * Computes responsive inspector panel width, overlay fallback decision, and image bounding box.
     */
    public static InspectorLayout computeLayout(int screenWidth, int screenHeight, boolean panelOpen) {
        int safeW = Math.max(1, screenWidth);
        int safeH = Math.max(1, screenHeight);
        int maxImgHeight = Math.max(1, (int) (safeH * 0.65));

        if (!panelOpen) {
            int maxImgWidth = Math.max(1, (int) (safeW * 0.80));
            return new InspectorLayout(false, false, 0, safeW, maxImgWidth, maxImgHeight);
        }

        boolean overlay = safeW < NARROW_SCREEN_THRESHOLD;
        if (overlay) {
            int maxAllowed = Math.max(1, safeW - 16);
            int minClamped = Math.min(MIN_PANEL_WIDTH, maxAllowed);
            int maxClamped = Math.min(MAX_PANEL_WIDTH, maxAllowed);
            int desired = (int) Math.round(safeW * 0.55);
            int panelWidth = clamp(desired, minClamped, maxClamped);
            int maxImgWidth = Math.max(1, (int) (safeW * 0.80));
            return new InspectorLayout(true, true, panelWidth, safeW, maxImgWidth, maxImgHeight);
        }

        int desiredPanel = (int) Math.round(safeW * 0.28);
        int panelWidth = clamp(desiredPanel, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH);
        int leftPaneWidth = Math.max(1, safeW - panelWidth - PANEL_GAP - (HORIZONTAL_MARGIN * 2));
        int maxImgWidth = Math.max(1, (int) (leftPaneWidth * 0.92));
        return new InspectorLayout(true, false, panelWidth, leftPaneWidth, maxImgWidth, maxImgHeight);
    }

    /**
     * Computes a stable footer bar width that accommodates all toolbar buttons without exceeding the viewport.
     */
    public static int computeFooterWidth(int screenWidth, int referenceImageWidth, int minButtonWidth) {
        int safeW = Math.max(1, screenWidth);
        int desired = Math.max(referenceImageWidth, minButtonWidth);
        int maxViewportWidth = Math.max(1, safeW - 16);
        if (safeW >= minButtonWidth + 16) {
            return Math.min(desired, maxViewportWidth);
        }
        return Math.max(minButtonWidth, referenceImageWidth);
    }

    private static void addRowIfMeaningful(List<MetadataRow> rows, String label, String value, String copyValue) {
        if (isMeaningful(value)) {
            rows.add(new MetadataRow(label, value.trim(), copyValue));
        }
    }

    private static boolean isMeaningful(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty() && !"unknown".equalsIgnoreCase(trimmed);
    }

    private static String formatGameMode(String gameMode) {
        if (!isMeaningful(gameMode)) {
            return null;
        }
        String trimmed = gameMode.trim();
        if (trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
            return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
        }
        return trimmed;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
