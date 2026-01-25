package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class MetadataHandler {

    /**
     * Writes metadata to a PNG file asynchronously.
     */
    public static void writeMetadataAsync(File file, ScreenshotMetadata metadata) {
        System.out.println(
                "[Screenshot Manager] MetadataHandler: writeMetadataAsync called for: " + file.getAbsolutePath());
        Thread thread = new Thread(() -> {
            try {
                System.out.println("[Screenshot Manager] MetadataHandler: Background thread started");
                writeMetadata(file, metadata);
                System.out.println("[Screenshot Manager] MetadataHandler: Metadata write completed successfully!");
            } catch (Exception e) {
                System.err.println("[Screenshot Manager Enhanced] Failed to write metadata: " + e.getMessage());
                e.printStackTrace();
            }
        }, "ScreenshotMetadataWriter");

        thread.setDaemon(true);
        thread.start();
        System.out.println("[Screenshot Manager] MetadataHandler: Background thread started");
    }

    /**
     * Synchronous metadata writer (called by async thread).
     */
    private static void writeMetadata(File file, ScreenshotMetadata metadata) throws Exception {
        System.out.println("[Screenshot Manager] MetadataHandler: Waiting for file to be fully written...");

        // Wait for file to be fully written (size stability check)
        long lastSize = -1;
        int stableCount = 0;
        int maxAttempts = 50; // 5 seconds max
        int attempts = 0;

        while (stableCount < 3 && attempts < maxAttempts) {
            Thread.sleep(100);
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

        System.out.println("[Screenshot Manager] MetadataHandler: File is stable, size: " + file.length() + " bytes");
        System.out.println("[Screenshot Manager] MetadataHandler: Reading PNG file...");

        // 1. Read the existing PNG
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("ImageIO.read() returned null - file may be corrupted or still being written");
        }
        System.out.println("[Screenshot Manager] MetadataHandler: Image read successfully, size: " + image.getWidth()
                + "x" + image.getHeight());

        // 2. Construct XMP XML
        System.out.println("[Screenshot Manager] MetadataHandler: Building XMP XML...");
        String xmpXml = new XmpBuilder()
                .setTitle("Screenshot Manager Enhanced")
                .setDescription(createSummary(metadata))
                .addCustomProperty("mc", "WorldTitle", metadata.worldName)
                .addCustomProperty("mc", "Difficulty", metadata.difficulty)
                .addCustomProperty("mc", "Version", metadata.minecraftVersion)
                .addCustomProperty("mc", "Dimension", metadata.dimension)
                .addCustomProperty("mc", "Biome", metadata.biome)
                .addCustomProperty("mc", "Coordinates", metadata.coordinates)
                .addCustomProperty("mc", "Time", metadata.daysPlayed)
                .addCustomProperty("mc", "WorldAge", metadata.worldAge)
                .build();
        System.out.println("[Screenshot Manager] MetadataHandler: XMP XML built (" + xmpXml.length() + " bytes)");

        // 3. Write to temp file with XMP chunk
        File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
        System.out.println("[Screenshot Manager] MetadataHandler: Writing PNG with XMP to temp file: "
                + tempFile.getAbsolutePath());
        writePngWithXmp(image, tempFile, xmpXml);
        System.out.println(
                "[Screenshot Manager] MetadataHandler: Temp file written, size: " + tempFile.length() + " bytes");

        // 4. Atomic replace
        if (tempFile.exists() && tempFile.length() > 0) {
            System.out.println("[Screenshot Manager] MetadataHandler: Performing atomic replace...");
            if (!file.delete()) {
                throw new IOException("Failed to delete original file: " + file.getAbsolutePath());
            }
            if (!tempFile.renameTo(file)) {
                throw new IOException("Failed to rename temp file to: " + file.getAbsolutePath());
            }
            System.out.println("[Screenshot Manager] MetadataHandler: Atomic replace completed");
        } else {
            throw new IOException("Temp file is missing or empty");
        }
    }

    /**
     * Writes a PNG with an iTXt chunk containing XMP data.
     * The iTXt chunk is placed after IHDR but before IDAT for proper PNG structure.
     */
    private static void writePngWithXmp(BufferedImage image, File output, String xmpXml) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] pngData = baos.toByteArray();

        // PNG structure: signature (8 bytes) + chunks
        // We need to insert iTXt after IHDR (first chunk after signature) but before
        // IDAT
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
            // Write everything before IDAT (includes signature, IHDR, and any other
            // metadata chunks)
            fos.write(pngData, 0, idatPos);

            // Write iTXt chunk with XMP (before IDAT)
            writeItxtChunk(fos, "XML:com.adobe.xmp", xmpXml);

            // Write the rest of the file (IDAT chunks and IEND)
            fos.write(pngData, idatPos, pngData.length - idatPos);
        }
    }

    /**
     * Finds the position of a specific chunk type in PNG data.
     * Returns -1 if not found.
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
                return pos; // Return position of chunk start (length field)
            }

            // Move to next chunk: length(4) + type(4) + data(length) + crc(4)
            pos += 4 + 4 + length + 4;
        }

        return -1;
    }

    /**
     * Writes an iTXt PNG chunk.
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

    private static String createSummary(ScreenshotMetadata metadata) {
        return String.format(
                "World: %s | Dim: %s | Loc: %s | Age: %s",
                metadata.worldName,
                metadata.dimension,
                metadata.coordinates,
                metadata.daysPlayed);
    }

    /**
     * Data class for screenshot metadata.
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
     * Simple XML builder helper to avoid yet another dependency.
     */
    private static class XmpBuilder {
        private String title = "";
        private String description = "";
        private final StringBuilder customProperties = new StringBuilder();

        public XmpBuilder setTitle(String title) {
            this.title = escapeXml(title);
            return this;
        }

        public XmpBuilder setDescription(String description) {
            this.description = escapeXml(description);
            return this;
        }

        public XmpBuilder addCustomProperty(String namespace, String key, String value) {
            customProperties.append(String.format("      <%s:%s>%s</%s:%s>\n",
                    namespace, key, escapeXml(value), namespace, key));
            return this;
        }

        public String build() {
            return String.format(
                    "<x:xmpmeta xmlns:x='adobe:ns:meta/' >\n" +
                            "  <rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>\n" +
                            "    <rdf:Description rdf:about=''\n" +
                            "        xmlns:dc='http://purl.org/dc/elements/1.1/'\n" +
                            "        xmlns:xmp='http://ns.adobe.com/xap/1.0/'\n" +
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
