package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetadataHandler.
 * 
 * <p>
 * Tests cover XMP building, PNG chunk manipulation, and file operations.
 * Private methods are tested via reflection where necessary.
 * </p>
 */
public class MetadataHandlerTest {

    @TempDir
    Path tempDir;

    // =====================================================
    // ScreenshotMetadata Tests
    // =====================================================

    @Test
    void testScreenshotMetadataCreation() {
        MetadataHandler.ScreenshotMetadata metadata = new MetadataHandler.ScreenshotMetadata(
                "Test World",
                "overworld",
                "100, 64, -200",
                "42",
                "TestPlayer",
                "Normal",
                "Survival",
                "1.21.10",
                "minecraft:plains",
                "1234567890");

        assertEquals("Test World", metadata.worldName);
        assertEquals("overworld", metadata.dimension);
        assertEquals("100, 64, -200", metadata.coordinates);
        assertEquals("42", metadata.daysPlayed);
        assertEquals("TestPlayer", metadata.playerName);
        assertEquals("Normal", metadata.difficulty);
        assertEquals("Survival", metadata.gameMode);
        assertEquals("1.21.10", metadata.minecraftVersion);
        assertEquals("minecraft:plains", metadata.biome);
        assertEquals("1234567890", metadata.worldAge);
    }

    @Test
    void testScreenshotMetadataWithNullValues() {
        MetadataHandler.ScreenshotMetadata metadata = new MetadataHandler.ScreenshotMetadata(
                null, null, null, null, null, null, null, null, null, null);

        assertNull(metadata.worldName);
        assertNull(metadata.dimension);
        assertNull(metadata.coordinates);
    }

    // =====================================================
    // XmpBuilder Tests (via reflection)
    // =====================================================

    @Test
    void testXmpBuilderBasicOutput() throws Exception {
        // Access private XmpBuilder class via reflection
        Class<?> xmpBuilderClass = Class.forName(
                "com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.MetadataHandler$XmpBuilder");

        var constructor = xmpBuilderClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object builder = constructor.newInstance();

        Method setTitle = xmpBuilderClass.getDeclaredMethod("setTitle", String.class);
        setTitle.setAccessible(true);
        Method setDescription = xmpBuilderClass.getDeclaredMethod("setDescription", String.class);
        setDescription.setAccessible(true);
        Method build = xmpBuilderClass.getDeclaredMethod("build");
        build.setAccessible(true);

        setTitle.invoke(builder, "Test Title");
        setDescription.invoke(builder, "Test Description");

        String result = (String) build.invoke(builder);

        assertNotNull(result);
        assertTrue(result.contains("<x:xmpmeta"));
        assertTrue(result.contains("Test Title"));
        assertTrue(result.contains("Test Description"));
        assertTrue(result.contains("rdf:RDF"));
    }

    @Test
    void testXmpBuilderCustomProperties() throws Exception {
        Class<?> xmpBuilderClass = Class.forName(
                "com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.MetadataHandler$XmpBuilder");

        var constructor = xmpBuilderClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object builder = constructor.newInstance();

        Method addCustomProperty = xmpBuilderClass.getDeclaredMethod("addCustomProperty",
                String.class, String.class, String.class);
        addCustomProperty.setAccessible(true);
        Method build = xmpBuilderClass.getDeclaredMethod("build");
        build.setAccessible(true);

        addCustomProperty.invoke(builder, "mc", "WorldTitle", "My World");
        addCustomProperty.invoke(builder, "mc", "Dimension", "overworld");

        String result = (String) build.invoke(builder);

        assertTrue(result.contains("<mc:WorldTitle>My World</mc:WorldTitle>"));
        assertTrue(result.contains("<mc:Dimension>overworld</mc:Dimension>"));
    }

    @Test
    void testXmpBuilderXmlEscaping() throws Exception {
        Class<?> xmpBuilderClass = Class.forName(
                "com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.MetadataHandler$XmpBuilder");

        var constructor = xmpBuilderClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object builder = constructor.newInstance();

        Method setTitle = xmpBuilderClass.getDeclaredMethod("setTitle", String.class);
        setTitle.setAccessible(true);
        Method build = xmpBuilderClass.getDeclaredMethod("build");
        build.setAccessible(true);

        // Test XML special characters
        setTitle.invoke(builder, "Test <World> & \"Quotes\" 'Apostrophe'");

        String result = (String) build.invoke(builder);

        assertTrue(result.contains("&lt;World&gt;"));
        assertTrue(result.contains("&amp;"));
        assertTrue(result.contains("&quot;Quotes&quot;"));
        assertTrue(result.contains("&apos;Apostrophe&apos;"));
    }

    @Test
    void testXmpBuilderNullHandling() throws Exception {
        Class<?> xmpBuilderClass = Class.forName(
                "com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.MetadataHandler$XmpBuilder");

        var constructor = xmpBuilderClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object builder = constructor.newInstance();

        Method setTitle = xmpBuilderClass.getDeclaredMethod("setTitle", String.class);
        setTitle.setAccessible(true);
        Method build = xmpBuilderClass.getDeclaredMethod("build");
        build.setAccessible(true);

        // Pass null title - should not throw
        setTitle.invoke(builder, (String) null);

        String result = (String) build.invoke(builder);
        assertNotNull(result);
        // Should have empty title, not "null"
        assertFalse(result.contains(">null<"));
    }

    // =====================================================
    // findChunkPosition Tests (via reflection)
    // =====================================================

    @Test
    void testFindChunkPositionWithValidPng() throws Exception {
        // Create a minimal valid PNG structure
        byte[] pngData = createMinimalPng();

        Method findChunkPosition = MetadataHandler.class.getDeclaredMethod(
                "findChunkPosition", byte[].class, String.class);
        findChunkPosition.setAccessible(true);

        // Find IHDR (should be at position 8, right after signature)
        int ihdrPos = (int) findChunkPosition.invoke(null, pngData, "IHDR");
        assertEquals(8, ihdrPos);

        // Find IDAT
        int idatPos = (int) findChunkPosition.invoke(null, pngData, "IDAT");
        assertTrue(idatPos > 8, "IDAT should come after IHDR");

        // Find IEND
        int iendPos = (int) findChunkPosition.invoke(null, pngData, "IEND");
        assertTrue(iendPos > idatPos, "IEND should come after IDAT");
    }

    @Test
    void testFindChunkPositionNotFound() throws Exception {
        byte[] pngData = createMinimalPng();

        Method findChunkPosition = MetadataHandler.class.getDeclaredMethod(
                "findChunkPosition", byte[].class, String.class);
        findChunkPosition.setAccessible(true);

        // Try to find a chunk that doesn't exist
        int pos = (int) findChunkPosition.invoke(null, pngData, "tEXt");
        assertEquals(-1, pos);
    }

    // =====================================================
    // writePngWithXmp Tests (via reflection)
    // =====================================================

    @Test
    void testWritePngWithXmpCreatesValidOutput() throws Exception {
        byte[] originalPng = createMinimalPng();
        File outputFile = tempDir.resolve("output.png").toFile();
        String xmpXml = "<x:xmpmeta>test</x:xmpmeta>";

        Method writePngWithXmp = MetadataHandler.class.getDeclaredMethod(
                "writePngWithXmp", byte[].class, File.class, String.class);
        writePngWithXmp.setAccessible(true);

        writePngWithXmp.invoke(null, originalPng, outputFile, xmpXml);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > originalPng.length,
                "Output should be larger due to added XMP chunk");

        // Verify PNG signature is preserved
        byte[] outputData = Files.readAllBytes(outputFile.toPath());
        assertPngSignature(outputData);
    }

    @Test
    void testWritePngWithXmpContainsItxtChunk() throws Exception {
        byte[] originalPng = createMinimalPng();
        File outputFile = tempDir.resolve("output_with_itxt.png").toFile();
        String xmpXml = "<x:xmpmeta>test content</x:xmpmeta>";

        Method writePngWithXmp = MetadataHandler.class.getDeclaredMethod(
                "writePngWithXmp", byte[].class, File.class, String.class);
        writePngWithXmp.setAccessible(true);

        writePngWithXmp.invoke(null, originalPng, outputFile, xmpXml);

        byte[] outputData = Files.readAllBytes(outputFile.toPath());

        // Find iTXt chunk
        Method findChunkPosition = MetadataHandler.class.getDeclaredMethod(
                "findChunkPosition", byte[].class, String.class);
        findChunkPosition.setAccessible(true);

        int itxtPos = (int) findChunkPosition.invoke(null, outputData, "iTXt");
        assertTrue(itxtPos > 0, "iTXt chunk should exist in output");

        // Verify iTXt comes before IDAT
        int idatPos = (int) findChunkPosition.invoke(null, outputData, "IDAT");
        assertTrue(itxtPos < idatPos, "iTXt should be placed before IDAT");
    }

    // =====================================================
    // writeItxtChunk Tests (via reflection)
    // =====================================================

    @Test
    void testWriteItxtChunkFormat() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String keyword = "XML:com.adobe.xmp";
        String text = "<test>content</test>";

        Method writeItxtChunk = MetadataHandler.class.getDeclaredMethod(
                "writeItxtChunk", OutputStream.class, String.class, String.class);
        writeItxtChunk.setAccessible(true);

        writeItxtChunk.invoke(null, baos, keyword, text);

        byte[] chunk = baos.toByteArray();

        // Verify chunk structure:
        // 4 bytes length + 4 bytes "iTXt" + data + 4 bytes CRC
        assertTrue(chunk.length > 12);

        // Verify chunk type is "iTXt"
        assertEquals('i', (char) chunk[4]);
        assertEquals('T', (char) chunk[5]);
        assertEquals('X', (char) chunk[6]);
        assertEquals('t', (char) chunk[7]);
    }

    // =====================================================
    // createSummary Tests (via reflection)
    // =====================================================

    @Test
    void testCreateSummary() throws Exception {
        MetadataHandler.ScreenshotMetadata metadata = new MetadataHandler.ScreenshotMetadata(
                "MyWorld",
                "the_nether",
                "0, 64, 0",
                "100",
                "Player1",
                "Hard",
                "Creative",
                "1.21.10",
                "minecraft:nether_wastes",
                "500000");

        Method createSummary = MetadataHandler.class.getDeclaredMethod(
                "createSummary", MetadataHandler.ScreenshotMetadata.class);
        createSummary.setAccessible(true);

        String summary = (String) createSummary.invoke(null, metadata);

        assertTrue(summary.contains("MyWorld"));
        assertTrue(summary.contains("the_nether"));
        assertTrue(summary.contains("0, 64, 0"));
        assertTrue(summary.contains("100"));
    }

    // =====================================================
    // waitForFileStability Tests (via reflection)
    // =====================================================

    @Test
    void testWaitForFileStabilityWithExistingFile() throws Exception {
        // Create a file that already exists and is stable
        File testFile = tempDir.resolve("stable.png").toFile();
        Files.write(testFile.toPath(), createMinimalPng());

        Method waitForFileStability = MetadataHandler.class.getDeclaredMethod(
                "waitForFileStability", File.class);
        waitForFileStability.setAccessible(true);

        // Should not throw
        assertDoesNotThrow(() -> waitForFileStability.invoke(null, testFile));
    }

    @Test
    void testWaitForFileStabilityWithMissingFile() throws Exception {
        File missingFile = tempDir.resolve("nonexistent.png").toFile();

        Method waitForFileStability = MetadataHandler.class.getDeclaredMethod(
                "waitForFileStability", File.class);
        waitForFileStability.setAccessible(true);

        // Should throw IOException (wrapped in InvocationTargetException)
        Exception exception = assertThrows(Exception.class,
                () -> waitForFileStability.invoke(null, missingFile));

        assertTrue(exception.getCause() instanceof IOException);
    }

    // =====================================================
    // Integration Tests
    // =====================================================

    @Test
    void testFullMetadataEmbeddingWorkflow() throws Exception {
        // Create a PNG file
        File pngFile = tempDir.resolve("screenshot.png").toFile();
        byte[] originalPng = createMinimalPng();
        Files.write(pngFile.toPath(), originalPng);

        long originalSize = pngFile.length();

        // Create metadata
        MetadataHandler.ScreenshotMetadata metadata = new MetadataHandler.ScreenshotMetadata(
                "Integration Test World",
                "overworld",
                "123, 64, -456",
                "7",
                "Tester",
                "Normal",
                "Survival",
                "1.21.10",
                "minecraft:forest",
                "86400000");

        // Call private writeMetadata method
        Method writeMetadata = MetadataHandler.class.getDeclaredMethod(
                "writeMetadata", File.class, MetadataHandler.ScreenshotMetadata.class);
        writeMetadata.setAccessible(true);

        writeMetadata.invoke(null, pngFile, metadata);

        // Verify file was modified
        assertTrue(pngFile.exists());
        assertTrue(pngFile.length() > originalSize, "File should be larger after embedding");

        // Verify PNG is still valid
        byte[] modifiedPng = Files.readAllBytes(pngFile.toPath());
        assertPngSignature(modifiedPng);

        // Verify iTXt chunk exists
        Method findChunkPosition = MetadataHandler.class.getDeclaredMethod(
                "findChunkPosition", byte[].class, String.class);
        findChunkPosition.setAccessible(true);

        int itxtPos = (int) findChunkPosition.invoke(null, modifiedPng, "iTXt");
        assertTrue(itxtPos > 0, "iTXt chunk should exist");
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    /**
     * Creates a minimal valid PNG file structure.
     * This is a 1x1 white pixel PNG.
     */
    private byte[] createMinimalPng() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // PNG Signature
        baos.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });

        // IHDR chunk (13 bytes of data)
        byte[] ihdrData = new byte[] {
                0, 0, 0, 1, // width = 1
                0, 0, 0, 1, // height = 1
                8, // bit depth = 8
                2, // color type = RGB
                0, // compression method
                0, // filter method
                0 // interlace method
        };
        writeChunk(baos, "IHDR", ihdrData);

        // IDAT chunk (minimal compressed data - just a white pixel)
        // This is zlib-compressed data for a single white pixel
        byte[] idatData = new byte[] {
                0x78, (byte) 0x9C, 0x62, (byte) 0xF8,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00,
                0x05, (byte) 0xFE, 0x02, (byte) 0xFE
        };
        writeChunk(baos, "IDAT", idatData);

        // IEND chunk (0 bytes of data)
        writeChunk(baos, "IEND", new byte[0]);

        return baos.toByteArray();
    }

    private void writeChunk(ByteArrayOutputStream baos, String type, byte[] data) throws IOException {
        // Length (4 bytes, big-endian)
        baos.write((data.length >> 24) & 0xFF);
        baos.write((data.length >> 16) & 0xFF);
        baos.write((data.length >> 8) & 0xFF);
        baos.write(data.length & 0xFF);

        // Type (4 bytes)
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        baos.write(typeBytes);

        // Data
        baos.write(data);

        // CRC (4 bytes)
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long crcValue = crc.getValue();
        baos.write((int) ((crcValue >> 24) & 0xFF));
        baos.write((int) ((crcValue >> 16) & 0xFF));
        baos.write((int) ((crcValue >> 8) & 0xFF));
        baos.write((int) (crcValue & 0xFF));
    }

    private void assertPngSignature(byte[] data) {
        assertTrue(data.length >= 8, "Data too short for PNG signature");
        assertEquals((byte) 0x89, data[0]);
        assertEquals('P', (char) data[1]);
        assertEquals('N', (char) data[2]);
        assertEquals('G', (char) data[3]);
        assertEquals(0x0D, data[4]);
        assertEquals(0x0A, data[5]);
        assertEquals(0x1A, data[6]);
        assertEquals(0x0A, data[7]);
    }
}
