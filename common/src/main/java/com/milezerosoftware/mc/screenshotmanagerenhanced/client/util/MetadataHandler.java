package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

/**
 * Handles embedding XMP metadata into PNG screenshot files.
 * 
 * <p>
 * This class provides functionality to asynchronously write XMP metadata
 * into PNG files by inserting an iTXt chunk before the IDAT chunks.
 * The metadata includes Minecraft-specific information such as world name,
 * dimension, coordinates, and biome.
 * </p>
 * 
 * <p>
 * The writing process is thread-safe and uses file size stability checks
 * to ensure the screenshot file is fully written before attempting to read it.
 * </p>
 */
public class MetadataHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("screenshot-manager-enhanced");

    private static final int FILE_STABILITY_CHECKS = 3;
    private static final int MAX_WAIT_ATTEMPTS = 50;
    private static final int POLL_INTERVAL_MS = 100;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ScreenshotMetadataWriter");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Writes XMP metadata to a PNG file asynchronously.
     * 
     * <p>
     * Submits a task to a shared single-threaded daemon executor that waits for the
     * file to exist and stabilize in size, then embeds the provided metadata as XMP
     * data in the PNG.
     * </p>
     *
     * @param file     The PNG screenshot file to modify
     * @param metadata The metadata to embed
     */
    public static void writeMetadataAsync(File file, ScreenshotMetadata metadata) {
        EXECUTOR.submit(() -> {
            try {
                writeMetadata(file, metadata);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Log the exception - metadata embedding is non-critical but errors aid
                // debugging
                LOGGER.error("Failed to embed metadata for {}: {}", file.getName(), e.getMessage(), e);
            }
        });
    }

    /**
     * Synchronously writes XMP metadata to a PNG file.
     * 
     * <p>
     * This method performs the following steps:
     * </p>
     * <ol>
     * <li>Waits for the file to exist and be fully written (size stability check)</li>
     * <li>Reads the existing PNG image</li>
     * <li>Constructs XMP XML with the provided metadata</li>
     * <li>Writes a new PNG with the XMP chunk inserted before IDAT</li>
     * <li>Atomically replaces the original file (with non-atomic fallback)</li>
     * </ol>
     *
     * @param file     The PNG file to modify
     * @param metadata The metadata to embed
     * @throws Exception If any step of the process fails
     */
    private static void writeMetadata(File file, ScreenshotMetadata metadata) throws Exception {
        // Wait for file to exist and be fully written (size stability check)
        waitForFileStability(file);

        // Read the existing PNG bytes directly (avoids slow re-encoding)
        byte[] pngData = Files.readAllBytes(file.toPath());
        if (pngData.length == 0) {
            throw new IOException("File is empty - may be corrupted: " + file.getAbsolutePath());
        }

        // Construct XMP XML
        String xmpXml = buildXmpXml(metadata);

        // Write to temp file with XMP chunk
        File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            writePngWithXmp(pngData, tempFile, xmpXml);

            // Atomic replace using Files.move (safer on Windows, prevents data loss)
            if (tempFile.exists() && tempFile.length() > 0) {
                try {
                    Files.move(tempFile.toPath(), file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile.toPath(), file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                throw new IOException("Temp file is missing or empty");
            }
        } finally {
            if (tempFile.exists()) {
                Files.deleteIfExists(tempFile.toPath());
            }
        }
    }

    /**
     * Waits for a file to exist and be fully written by checking size stability.
     * 
     * <p>
     * Polls the file size every {@link #POLL_INTERVAL_MS}ms and considers
     * the file stable after {@link #FILE_STABILITY_CHECKS} consecutive readings
     * with the same non-zero size.
     * </p>
     *
     * @param file The file to wait for
     * @throws IOException          If the file doesn't exist or is empty after waiting
     * @throws InterruptedException If the waiting thread is interrupted
     */
    private static void waitForFileStability(File file) throws IOException, InterruptedException {
        long lastSize = -1;
        int stableCount = 0;
        int attempts = 0;

        while (stableCount < FILE_STABILITY_CHECKS && attempts < MAX_WAIT_ATTEMPTS) {
            Thread.sleep(POLL_INTERVAL_MS);
            attempts++;

            if (!file.exists()) {
                stableCount = 0;
                lastSize = -1;
                continue;
            }

            long currentSize = file.length();
            if (currentSize > 0 && currentSize == lastSize) {
                stableCount++;
            } else {
                stableCount = 0;
            }
            lastSize = currentSize;
        }

        if (!file.exists() || file.length() == 0) {
            throw new IOException("File does not exist or is empty after waiting: " + file.getAbsolutePath());
        }
    }

    /**
     * Writes a PNG with an iTXt chunk containing XMP data.
     * 
     * <p>
     * The iTXt chunk is placed after IHDR but before IDAT for proper PNG structure,
     * avoiding the "Text chunk found after IDAT" warning from some readers.
     * </p>
     *
     * @param pngData The raw PNG byte data
     * @param output  The output file
     * @param xmpXml  The XMP XML string to embed
     * @throws IOException If writing fails
     */
    private static void writePngWithXmp(byte[] pngData, File output, String xmpXml) throws IOException {

        // Find the first IDAT chunk position
        int idatPos = findChunkPosition(pngData, "IDAT");

        if (idatPos == -1) {
            // Fallback: just append before IEND
            int iendPos = pngData.length - 12;
            if (iendPos < 0) {
                throw new IOException("Invalid PNG data: file too short");
            }
            try (FileOutputStream fos = new FileOutputStream(output)) {
                fos.write(pngData, 0, iendPos);
                writeItxtChunk(fos, "XML:com.adobe.xmp", xmpXml);
                fos.write(pngData, iendPos, 12);
            }
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(output)) {
            // Write everything before IDAT
            fos.write(pngData, 0, idatPos);

            // Write iTXt chunk with XMP (before IDAT)
            writeItxtChunk(fos, "XML:com.adobe.xmp", xmpXml);

            // Write the rest of the file (IDAT chunks and IEND)
            fos.write(pngData, idatPos, pngData.length - idatPos);
        }
    }

    /**
     * Finds the position of a specific chunk type in PNG data.
     *
     * @param pngData   The raw PNG byte data
     * @param chunkType The 4-character chunk type to find (e.g., "IDAT")
     * @return The byte position of the chunk, or -1 if not found or PNG data is malformed
     */
    private static int findChunkPosition(byte[] pngData, String chunkType) {
        if (pngData == null || pngData.length < 8) {
            return -1;
        }

        // Skip PNG signature (8 bytes)
        int pos = 8;
        byte[] typeBytes = chunkType.getBytes(StandardCharsets.ISO_8859_1);

        while (pos + 8 <= pngData.length) {
            // Read chunk length (4 bytes, big-endian)
            int length = ((pngData[pos] & 0xFF) << 24) |
                    ((pngData[pos + 1] & 0xFF) << 16) |
                    ((pngData[pos + 2] & 0xFF) << 8) |
                    (pngData[pos + 3] & 0xFF);

            // Validate chunk length and guard against integer overflow / out-of-bounds
            long nextPos = pos + 12L + length;
            if (length < 0 || nextPos > pngData.length || nextPos <= pos) {
                return -1;
            }

            // Check chunk type (4 bytes at pos+4)
            boolean match = true;
            for (int i = 0; i < 4 && i < typeBytes.length; i++) {
                if (pngData[pos + 4 + i] != typeBytes[i]) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return pos;
            }

            // Move to next chunk: length(4) + type(4) + data(length) + crc(4)
            pos = (int) nextPos;
        }

        return -1;
    }

    /**
     * Writes an iTXt PNG chunk to the output stream.
     *
     * @param os      The output stream
     * @param keyword The chunk keyword (e.g., "XML:com.adobe.xmp")
     * @param text    The text content (XMP XML)
     * @throws IOException If writing fails
     */
    private static void writeItxtChunk(OutputStream os, String keyword, String text) throws IOException {
        ByteArrayOutputStream chunkData = new ByteArrayOutputStream();

        // Keyword (null-terminated)
        chunkData.write(keyword.getBytes(StandardCharsets.ISO_8859_1));
        chunkData.write(0);

        // Compression flag (0 = uncompressed)
        chunkData.write(0);

        // Compression method (must be 0)
        chunkData.write(0);

        // Language tag (empty, null-terminated)
        chunkData.write(0);

        // Translated keyword (empty, null-terminated)
        chunkData.write(0);

        // Text (UTF-8)
        chunkData.write(text.getBytes(StandardCharsets.UTF_8));

        byte[] data = chunkData.toByteArray();

        // Write length (4 bytes, big-endian)
        writeInt(os, data.length);

        // Write chunk type "iTXt" (4 bytes)
        os.write("iTXt".getBytes(StandardCharsets.ISO_8859_1));

        // Write chunk data
        os.write(data);

        // Write CRC (4 bytes)
        CRC32 crc = new CRC32();
        crc.update("iTXt".getBytes(StandardCharsets.ISO_8859_1));
        crc.update(data);
        writeInt(os, (int) crc.getValue());
    }

    private static void writeInt(OutputStream os, int value) throws IOException {
        os.write((value >> 24) & 0xFF);
        os.write((value >> 16) & 0xFF);
        os.write((value >> 8) & 0xFF);
        os.write(value & 0xFF);
    }

    static String buildXmpXml(ScreenshotMetadata metadata) {
        XmpBuilder builder = new XmpBuilder()
                .setTitle("Screenshot Manager Enhanced")
                .setDescription(createSummary(metadata));

        if (metadata != null) {
            builder.addCustomProperty("mc", "WorldTitle", metadata.worldName)
                    .addCustomProperty("mc", "Difficulty", metadata.difficulty)
                    .addCustomProperty("mc", "Version", metadata.minecraftVersion)
                    .addCustomProperty("mc", "Dimension", metadata.dimension)
                    .addCustomProperty("mc", "Biome", metadata.biome)
                    .addCustomProperty("mc", "Coordinates", metadata.coordinates)
                    .addCustomProperty("mc", "Days", metadata.daysPlayed)
                    .addCustomProperty("mc", "WorldAge", metadata.worldAge);

            if (metadata.playerName != null && !metadata.playerName.isBlank()) {
                builder.addCustomProperty("mc", "PlayerName", metadata.playerName);
            }
            if (metadata.gameMode != null && !metadata.gameMode.isBlank()) {
                builder.addCustomProperty("mc", "GameMode", metadata.gameMode);
            }
        }

        return builder.build();
    }

    /**
     * Creates a human-readable summary string from metadata.
     *
     * @param metadata The screenshot metadata
     * @return A formatted summary string
     */
    private static String createSummary(ScreenshotMetadata metadata) {
        if (metadata == null) {
            return "World: Unknown | Dim: Unknown | Loc: Unknown | Days Played: Unknown";
        }
        return String.format(
                "World: %s | Dim: %s | Loc: %s | Days Played: %s",
                safeSummaryValue(metadata.worldName),
                safeSummaryValue(metadata.dimension),
                safeSummaryValue(metadata.coordinates),
                safeSummaryValue(metadata.daysPlayed));
    }

    private static String safeSummaryValue(String value) {
        return value != null && !value.isBlank() ? value : "Unknown";
    }

    /**
     * Data class containing screenshot metadata.
     * 
     * <p>
     * Immutable record of all metadata fields collected at screenshot time.
     * </p>
     */
    public static class ScreenshotMetadata {
        public final String worldName;
        public final String dimension;
        public final String coordinates;
        public final String daysPlayed;
        public final String playerName;
        public final String difficulty;
        public final String gameMode;
        public final String minecraftVersion;
        public final String biome;
        public final String worldAge;

        /**
         * Creates a new ScreenshotMetadata instance.
         *
         * @param worldName        The world or server name
         * @param dimension        The dimension (Overworld, Nether, End)
         * @param coordinates      Player coordinates (x, y, z format)
         * @param daysPlayed       In-game days elapsed
         * @param playerName       The player's name
         * @param difficulty       Game difficulty setting
         * @param gameMode         Current game mode
         * @param minecraftVersion Minecraft version string
         * @param biome            Current biome name
         * @param worldAge         Real-time world age
         */
        public ScreenshotMetadata(
                String worldName,
                String dimension,
                String coordinates,
                String daysPlayed,
                String playerName,
                String difficulty,
                String gameMode,
                String minecraftVersion,
                String biome,
                String worldAge) {
            this.worldName = worldName;
            this.dimension = dimension;
            this.coordinates = coordinates;
            this.daysPlayed = daysPlayed;
            this.playerName = playerName;
            this.difficulty = difficulty;
            this.gameMode = gameMode;
            this.minecraftVersion = minecraftVersion;
            this.biome = biome;
            this.worldAge = worldAge;
        }
    }

    /**
     * Builder for constructing XMP XML documents.
     * 
     * <p>
     * Provides a fluent API for building standards-compliant XMP metadata
     * with Dublin Core and custom Minecraft namespace properties.
     * </p>
     */
    private static class XmpBuilder {
        private String title = "";
        private String description = "";
        private final StringBuilder customProperties = new StringBuilder();

        /**
         * Sets the document title (dc:title).
         *
         * @param title The title string
         * @return This builder for chaining
         */
        public XmpBuilder setTitle(String title) {
            this.title = escapeXml(title);
            return this;
        }

        /**
         * Sets the document description (dc:description).
         *
         * @param description The description string
         * @return This builder for chaining
         */
        public XmpBuilder setDescription(String description) {
            this.description = escapeXml(description);
            return this;
        }

        /**
         * Adds a custom property in a specified namespace.
         *
         * @param namespace The XML namespace prefix (e.g., "mc")
         * @param key       The property name
         * @param value     The property value
         * @return This builder for chaining
         */
        public XmpBuilder addCustomProperty(String namespace, String key, String value) {
            customProperties.append(String.format("      <%s:%s>%s</%s:%s>\n",
                    namespace, key, escapeXml(value), namespace, key));
            return this;
        }

        /**
         * Builds the complete XMP XML document.
         *
         * @return The XMP XML string
         */
        public String build() {
            return String.format(
                    "<x:xmpmeta xmlns:x='adobe:ns:meta/' >\n" +
                            "  <rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>\n" +
                            "    <rdf:Description rdf:about=''\n" +
                            "        xmlns:dc='http://purl.org/dc/elements/1.1/'\n" +
                            "        xmlns:mc='http://milezerosoftware.com/mc/1.0/'>\n" +
                            "      <dc:title>\n" +
                            "        <rdf:Alt>\n" +
                            "          <rdf:li xml:lang='x-default'>%s</rdf:li>\n" +
                            "        </rdf:Alt>\n" +
                            "      </dc:title>\n" +
                            "      <dc:description>\n" +
                            "        <rdf:Alt>\n" +
                            "          <rdf:li xml:lang='x-default'>%s</rdf:li>\n" +
                            "        </rdf:Alt>\n" +
                            "      </dc:description>\n" +
                            "%s" +
                            "    </rdf:Description>\n" +
                            "  </rdf:RDF>\n" +
                            "</x:xmpmeta>",
                    title, description, customProperties.toString());
        }

        private String escapeXml(String input) {
            if (input == null)
                return "";
            return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }
    }
}
