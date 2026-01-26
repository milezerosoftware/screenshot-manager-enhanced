package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
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

    private static final int FILE_STABILITY_CHECKS = 3;
    private static final int MAX_WAIT_ATTEMPTS = 50;
    private static final int POLL_INTERVAL_MS = 100;

    /**
     * Writes XMP metadata to a PNG file asynchronously.
     * 
     * <p>
     * This method spawns a daemon thread that waits for the file to be fully
     * written, then embeds the provided metadata as XMP data in the PNG.
     * </p>
     *
     * @param file     The PNG screenshot file to modify
     * @param metadata The metadata to embed
     */
    public static void writeMetadataAsync(File file, ScreenshotMetadata metadata) {
        Thread thread = new Thread(() -> {
            try {
                writeMetadata(file, metadata);
            } catch (Exception e) {
                // Silently fail - metadata embedding is non-critical
            }
        }, "ScreenshotMetadataWriter");

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Synchronously writes XMP metadata to a PNG file.
     * 
     * <p>
     * This method performs the following steps:
     * </p>
     * <ol>
     * <li>Waits for the file to be fully written (size stability check)</li>
     * <li>Reads the existing PNG image</li>
     * <li>Constructs XMP XML with the provided metadata</li>
     * <li>Writes a new PNG with the XMP chunk inserted before IDAT</li>
     * <li>Atomically replaces the original file</li>
     * </ol>
     *
     * @param file     The PNG file to modify
     * @param metadata The metadata to embed
     * @throws Exception If any step of the process fails
     */
    private static void writeMetadata(File file, ScreenshotMetadata metadata) throws Exception {
        // Wait for file to be fully written (size stability check)
        waitForFileStability(file);

        // Read the existing PNG
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("ImageIO.read() returned null - file may be corrupted");
        }

        // Construct XMP XML
        String xmpXml = new XmpBuilder()
                .setTitle("Screenshot Manager Enhanced")
                .setDescription(createSummary(metadata))
                .addCustomProperty("mc", "WorldTitle", metadata.worldName)
                .addCustomProperty("mc", "Difficulty", metadata.difficulty)
                .addCustomProperty("mc", "Version", metadata.minecraftVersion)
                .addCustomProperty("mc", "Dimension", metadata.dimension)
                .addCustomProperty("mc", "Biome", metadata.biome)
                .addCustomProperty("mc", "Coordinates", metadata.coordinates)
                .addCustomProperty("mc", "Days", metadata.daysPlayed)
                .addCustomProperty("mc", "WorldAge", metadata.worldAge)
                .build();

        // Write to temp file with XMP chunk
        File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
        writePngWithXmp(image, tempFile, xmpXml);

        // Atomic replace
        if (tempFile.exists() && tempFile.length() > 0) {
            if (!file.delete()) {
                throw new IOException("Failed to delete original file: " + file.getAbsolutePath());
            }
            if (!tempFile.renameTo(file)) {
                throw new IOException("Failed to rename temp file to: " + file.getAbsolutePath());
            }
        } else {
            throw new IOException("Temp file is missing or empty");
        }
    }

    /**
     * Waits for a file to be fully written by checking size stability.
     * 
     * <p>
     * Polls the file size every {@link #POLL_INTERVAL_MS}ms and considers
     * the file stable after {@link #FILE_STABILITY_CHECKS} consecutive readings
     * with the same size.
     * </p>
     *
     * @param file The file to wait for
     * @throws IOException If the file doesn't exist or is empty after waiting
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
     * @param image  The image to write
     * @param output The output file
     * @param xmpXml The XMP XML string to embed
     * @throws IOException If writing fails
     */
    private static void writePngWithXmp(BufferedImage image, File output, String xmpXml) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] pngData = baos.toByteArray();

        // Find the first IDAT chunk position
        int idatPos = findChunkPosition(pngData, "IDAT");

        if (idatPos == -1) {
            // Fallback: just append before IEND
            int iendPos = pngData.length - 12;
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
     * @return The byte position of the chunk, or -1 if not found
     */
    private static int findChunkPosition(byte[] pngData, String chunkType) {
        // Skip PNG signature (8 bytes)
        int pos = 8;
        byte[] typeBytes = chunkType.getBytes(StandardCharsets.ISO_8859_1);

        while (pos + 8 < pngData.length) {
            // Read chunk length (4 bytes, big-endian)
            int length = ((pngData[pos] & 0xFF) << 24) |
                    ((pngData[pos + 1] & 0xFF) << 16) |
                    ((pngData[pos + 2] & 0xFF) << 8) |
                    (pngData[pos + 3] & 0xFF);

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
            pos += 4 + 4 + length + 4;
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

    /**
     * Creates a human-readable summary string from metadata.
     *
     * @param metadata The screenshot metadata
     * @return A formatted summary string
     */
    private static String createSummary(ScreenshotMetadata metadata) {
        return String.format(
                "World: %s | Dim: %s | Loc: %s | Days Played: %s",
                metadata.worldName,
                metadata.dimension,
                metadata.coordinates,
                metadata.daysPlayed);
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
