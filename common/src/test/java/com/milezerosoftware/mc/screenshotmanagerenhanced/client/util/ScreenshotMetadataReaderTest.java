package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader.FileInfo;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader.MetadataReadResult;
import com.milezerosoftware.mc.screenshotmanagerenhanced.client.util.ScreenshotMetadataReader.ParsedMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ScreenshotMetadataReaderTest {

    @TempDir
    Path tempDir;

    // =====================================================
    // Round-Trip Tests (MetadataHandler -> Reader)
    // =====================================================

    @Test
    void testFullRoundTripViaMetadataHandler() throws Exception {
        File pngFile = tempDir.resolve("roundtrip.png").toFile();
        Files.write(pngFile.toPath(), createMinimalPng(1920, 1080));

        MetadataHandler.ScreenshotMetadata written = new MetadataHandler.ScreenshotMetadata(
                "My Survival World",
                "Overworld",
                "x: 120, y: 64, z: -340",
                "42.50 d",
                "Steve",
                "Hard",
                "survival",
                "1.21.11",
                "Birch Forest",
                "3.25 d");

        invokeWriteMetadata(pngFile, written);

        MetadataReadResult result = ScreenshotMetadataReader.read(pngFile.toPath());
        assertTrue(result.hasXmpChunk());
        assertTrue(result.hasMetadata());

        FileInfo fileInfo = result.fileInfo();
        assertEquals(1920, fileInfo.imageWidth());
        assertEquals(1080, fileInfo.imageHeight());
        assertTrue(fileInfo.hasDimensions());
        assertEquals(Files.size(pngFile.toPath()), fileInfo.fileSizeBytes());
        assertTrue(fileInfo.lastModifiedMillis() > 0);

        ParsedMetadata parsed = result.metadata();
        assertEquals("My Survival World", parsed.worldName());
        assertEquals("Overworld", parsed.dimension());
        assertEquals("Birch Forest", parsed.biome());
        assertEquals("x: 120, y: 64, z: -340", parsed.coordinates());
        assertEquals("Hard", parsed.difficulty());
        assertEquals("42.50 d", parsed.daysPlayed());
        assertEquals("3.25 d", parsed.worldAge());
        assertEquals("1.21.11", parsed.minecraftVersion());
        assertEquals("Steve", parsed.playerName());
        assertEquals("survival", parsed.gameMode());
        assertNotNull(parsed.description());
        assertTrue(parsed.description().contains("My Survival World"));
    }

    @Test
    void testRoundTripWithXmlSpecialCharsUnicodeAndNullFields() throws Exception {
        File pngFile = tempDir.resolve("special_chars.png").toFile();
        Files.write(pngFile.toPath(), createMinimalPng(1280, 720));

        MetadataHandler.ScreenshotMetadata written = new MetadataHandler.ScreenshotMetadata(
                "Alex's <Base> & \"Friends\" 日本語",
                "The Nether",
                "x: -50, y: 72, z: 999",
                "10.00 d",
                null,
                "Normal",
                "   ",
                "26.2",
                "Crimson Forest — München",
                null);

        String xmpXml = MetadataHandler.buildXmpXml(written);
        assertFalse(xmpXml.contains("<mc:PlayerName>"), "Null playerName should be omitted");
        assertFalse(xmpXml.contains("<mc:GameMode>"), "Blank gameMode should be omitted");
        assertFalse(xmpXml.contains(">null<"), "Null fields should not serialize as literal 'null'");

        invokeWritePngWithXmp(createMinimalPng(1280, 720), pngFile, xmpXml);

        MetadataReadResult result = ScreenshotMetadataReader.read(pngFile.toPath());
        assertTrue(result.hasXmpChunk());
        assertTrue(result.hasMetadata());

        ParsedMetadata parsed = result.metadata();
        assertEquals("Alex's <Base> & \"Friends\" 日本語", parsed.worldName());
        assertEquals("The Nether", parsed.dimension());
        assertEquals("Crimson Forest — München", parsed.biome());
        assertEquals("x: -50, y: 72, z: 999", parsed.coordinates());
        assertEquals("Normal", parsed.difficulty());
        assertEquals("10.00 d", parsed.daysPlayed());
        assertEquals("26.2", parsed.minecraftVersion());
        assertNull(parsed.worldAge());
        assertNull(parsed.playerName());
        assertNull(parsed.gameMode());
    }

    // =====================================================
    // PNG Chunk Variations
    // =====================================================

    @Test
    void testPlainPngWithoutItxtChunk() throws Exception {
        Path pngPath = tempDir.resolve("plain.png");
        byte[] bytes = createMinimalPng(800, 600);
        Files.write(pngPath, bytes);

        MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
        assertFalse(result.hasXmpChunk());
        assertFalse(result.hasMetadata());
        assertEquals(800, result.fileInfo().imageWidth());
        assertEquals(600, result.fileInfo().imageHeight());
        assertEquals(bytes.length, result.fileInfo().fileSizeBytes());
        assertTrue(result.fileInfo().lastModifiedMillis() > 0);
        assertTrue(result.metadata().isEmpty());
    }

    @Test
    void testMultipleItxtChunksSkipsNonXmp() throws Exception {
        Path pngPath = tempDir.resolve("multi_itxt.png");
        String xmp = MetadataHandler.buildXmpXml(new MetadataHandler.ScreenshotMetadata(
                "MultiChunkWorld", "The End", "x: 0, y: 64, z: 0", "5.00 d",
                "Alex", "Peaceful", "creative", "26.1.2", "The End", "1.00 d"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePngHeaderAndIhdr(baos, 640, 360);
        writeItxtChunk(baos, "Comment", "Just a comment", false);
        writeItxtChunk(baos, "Author", "Someone", false);
        writeItxtChunk(baos, ScreenshotMetadataReader.XMP_KEYWORD, xmp, false);
        writeChunk(baos, "IDAT", minimalIdatData());
        writeChunk(baos, "IEND", new byte[0]);
        Files.write(pngPath, baos.toByteArray());

        MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
        assertTrue(result.hasXmpChunk());
        assertTrue(result.hasMetadata());
        assertEquals(640, result.fileInfo().imageWidth());
        assertEquals(360, result.fileInfo().imageHeight());
        assertEquals("MultiChunkWorld", result.metadata().worldName());
        assertEquals("Alex", result.metadata().playerName());
        assertEquals("creative", result.metadata().gameMode());
    }

    @Test
    void testItxtChunkAfterIdatIsFound() throws Exception {
        Path pngPath = tempDir.resolve("after_idat.png");
        String xmp = MetadataHandler.buildXmpXml(new MetadataHandler.ScreenshotMetadata(
                "PostIdatWorld", "Overworld", "x: 1, y: 2, z: 3", "1.00 d",
                "Builder", "Easy", "survival", "1.21.11", "Plains", "0.50 d"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePngHeaderAndIhdr(baos, 2560, 1440);
        // Large dummy IDAT payload to confirm O(1) FileChannel seek skips over IDAT cleanly
        writeChunk(baos, "IDAT", new byte[65536]);
        writeItxtChunk(baos, ScreenshotMetadataReader.XMP_KEYWORD, xmp, false);
        writeChunk(baos, "IEND", new byte[0]);
        Files.write(pngPath, baos.toByteArray());

        MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
        assertTrue(result.hasXmpChunk());
        assertTrue(result.hasMetadata());
        assertEquals(2560, result.fileInfo().imageWidth());
        assertEquals(1440, result.fileInfo().imageHeight());
        assertEquals("PostIdatWorld", result.metadata().worldName());
        assertEquals("Plains", result.metadata().biome());
    }

    @Test
    void testCompressedItxtChunk() throws Exception {
        Path pngPath = tempDir.resolve("compressed_xmp.png");
        String xmp = MetadataHandler.buildXmpXml(new MetadataHandler.ScreenshotMetadata(
                "CompressedWorld", "Overworld", "x: 42, y: 70, z: -15", "12.34 d",
                "ZipPlayer", "Normal", "adventure", "26.2", "Taiga", "2.00 d"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePngHeaderAndIhdr(baos, 1920, 1080);
        writeItxtChunk(baos, ScreenshotMetadataReader.XMP_KEYWORD, xmp, true);
        writeChunk(baos, "IDAT", minimalIdatData());
        writeChunk(baos, "IEND", new byte[0]);
        Files.write(pngPath, baos.toByteArray());

        MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
        assertTrue(result.hasXmpChunk());
        assertTrue(result.hasMetadata());
        assertEquals("CompressedWorld", result.metadata().worldName());
        assertEquals("ZipPlayer", result.metadata().playerName());
        assertEquals("adventure", result.metadata().gameMode());
        assertEquals("Taiga", result.metadata().biome());
    }

    @Test
    void testCompressedItxtExceedingDecompressionCapFailsSoftly() throws Exception {
        Path pngPath = tempDir.resolve("zipbomb.png");
        // Compress 1.2 MiB of repeating spaces (compresses to ~1.5 KB, inflates > 1 MiB cap)
        String hugePayload = " ".repeat(ScreenshotMetadataReader.MAX_ITXT_BYTES + 200_000);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePngHeaderAndIhdr(baos, 100, 100);
        writeItxtChunk(baos, ScreenshotMetadataReader.XMP_KEYWORD, hugePayload, true);
        writeChunk(baos, "IDAT", minimalIdatData());
        writeChunk(baos, "IEND", new byte[0]);
        Files.write(pngPath, baos.toByteArray());

        MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
        assertFalse(result.hasXmpChunk());
        assertFalse(result.hasMetadata());
        assertEquals(100, result.fileInfo().imageWidth());
        assertEquals(100, result.fileInfo().imageHeight());
    }

    @Test
    void testItxtWithNonEmptyLanguageTagAndTranslatedKeyword() throws Exception {
        String xmp = MetadataHandler.buildXmpXml(new MetadataHandler.ScreenshotMetadata(
                "LocalizedWorld", "Overworld", "x: 7, y: 80, z: 9", "2.00 d",
                "Polyglot", "Normal", "survival", "26.2", "Meadow", "0.25 d"));

        for (boolean compressed : new boolean[] { false, true }) {
            Path pngPath = tempDir.resolve("lang_tag_" + compressed + ".png");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writePngHeaderAndIhdr(baos, 640, 480);
            writeCustomItxtChunk(baos, ScreenshotMetadataReader.XMP_KEYWORD,
                    compressed ? 1 : 0, 0, "en-US", "XMP Metadata", xmp, compressed);
            writeChunk(baos, "IDAT", minimalIdatData());
            writeChunk(baos, "IEND", new byte[0]);
            Files.write(pngPath, baos.toByteArray());

            MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
            assertTrue(result.hasXmpChunk());
            assertTrue(result.hasMetadata());
            assertEquals("LocalizedWorld", result.metadata().worldName());
            assertEquals("Polyglot", result.metadata().playerName());
            assertEquals("Meadow", result.metadata().biome());
        }
    }

    @Test
    void testItxtMissingNullTerminatorsAndTruncatedPayloadsFailSoftly() throws Exception {
        byte[][] malformedChunks = new byte[][] {
                // No null terminator after keyword
                ScreenshotMetadataReader.XMP_KEYWORD.getBytes(StandardCharsets.ISO_8859_1),
                // Keyword + NUL + only 1 byte (missing compression method & remaining NULs)
                concat(ScreenshotMetadataReader.XMP_KEYWORD.getBytes(StandardCharsets.ISO_8859_1), new byte[] { 0, 0 }),
                // Missing language tag NUL terminator
                concat(ScreenshotMetadataReader.XMP_KEYWORD.getBytes(StandardCharsets.ISO_8859_1),
                        new byte[] { 0, 0, 0, 'e', 'n' }),
                // Missing translated keyword NUL terminator
                concat(ScreenshotMetadataReader.XMP_KEYWORD.getBytes(StandardCharsets.ISO_8859_1),
                        new byte[] { 0, 0, 0, 0, 'X', 'M', 'P' }),
                // Valid header but zero-length text payload
                concat(ScreenshotMetadataReader.XMP_KEYWORD.getBytes(StandardCharsets.ISO_8859_1),
                        new byte[] { 0, 0, 0, 0, 0 })
        };

        for (int i = 0; i < malformedChunks.length; i++) {
            Path pngPath = tempDir.resolve("malformed_itxt_" + i + ".png");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writePngHeaderAndIhdr(baos, 200, 150);
            writeChunk(baos, "iTXt", malformedChunks[i]);
            writeChunk(baos, "IDAT", minimalIdatData());
            writeChunk(baos, "IEND", new byte[0]);
            Files.write(pngPath, baos.toByteArray());

            MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
            assertFalse(result.hasXmpChunk(), "Malformed iTXt case " + i + " should not yield XMP");
            assertFalse(result.hasMetadata());
            assertEquals(200, result.fileInfo().imageWidth());
            assertEquals(150, result.fileInfo().imageHeight());
        }
    }

    @Test
    void testItxtUnsupportedCompressionFlagOrMethodFailsSoftly() throws Exception {
        int[][] unsupportedConfigs = { { 2, 0 }, { 1, 1 }, { 255, 0 } };
        for (int i = 0; i < unsupportedConfigs.length; i++) {
            Path pngPath = tempDir.resolve("unsupported_comp_" + i + ".png");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writePngHeaderAndIhdr(baos, 300, 200);
            writeCustomItxtChunk(baos, ScreenshotMetadataReader.XMP_KEYWORD,
                    unsupportedConfigs[i][0], unsupportedConfigs[i][1], "", "", "<x:xmpmeta/>", false);
            writeChunk(baos, "IDAT", minimalIdatData());
            writeChunk(baos, "IEND", new byte[0]);
            Files.write(pngPath, baos.toByteArray());

            MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
            assertFalse(result.hasXmpChunk());
            assertFalse(result.hasMetadata());
            assertEquals(300, result.fileInfo().imageWidth());
            assertEquals(200, result.fileInfo().imageHeight());
        }
    }

    @Test
    void testCompressedItxtCorruptZlibPayloadFailsSoftly() throws Exception {
        Path pngPath = tempDir.resolve("corrupt_zlib.png");
        ByteArrayOutputStream chunkData = new ByteArrayOutputStream();
        chunkData.write(ScreenshotMetadataReader.XMP_KEYWORD.getBytes(StandardCharsets.ISO_8859_1));
        chunkData.write(0); // keyword NUL
        chunkData.write(1); // compressionFlag = 1 (compressed)
        chunkData.write(0); // compressionMethod = 0 (zlib)
        chunkData.write(0); // empty language tag
        chunkData.write(0); // empty translated keyword
        chunkData.write(new byte[] { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0x01, 0x02 });

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePngHeaderAndIhdr(baos, 400, 300);
        writeChunk(baos, "iTXt", chunkData.toByteArray());
        writeChunk(baos, "IDAT", minimalIdatData());
        writeChunk(baos, "IEND", new byte[0]);
        Files.write(pngPath, baos.toByteArray());

        MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
        assertFalse(result.hasXmpChunk());
        assertFalse(result.hasMetadata());
        assertEquals(400, result.fileInfo().imageWidth());
        assertEquals(300, result.fileInfo().imageHeight());
    }

    // =====================================================
    // Attribute-style XMP (Lightroom / Photoshop round-trip)
    // =====================================================

    @Test
    void testAttributeStyleXmpParsing() {
        String attrXmp = "<?xpacket begin='\uFEFF' id='W5M0MpCehiHzreSzNTczkc9d'?>\n"
                + "<x:xmpmeta xmlns:x='adobe:ns:meta/'>\n"
                + "  <rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>\n"
                + "    <rdf:Description rdf:about=''\n"
                + "        xmlns:dc='http://purl.org/dc/elements/1.1/'\n"
                + "        xmlns:mc='http://milezerosoftware.com/mc/1.0/'\n"
                + "        mc:WorldTitle='Edited In Lightroom'\n"
                + "        mc:Dimension='Overworld'\n"
                + "        mc:Biome='Cherry Grove'\n"
                + "        mc:Coordinates='x: 10, y: 90, z: 20'\n"
                + "        mc:Difficulty='Hard'\n"
                + "        mc:Days='99.00 d'\n"
                + "        mc:WorldAge='14.50 d'\n"
                + "        mc:Version='26.2'\n"
                + "        mc:PlayerName='Photographer'\n"
                + "        mc:GameMode='spectator'/>\n"
                + "  </rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "<?xpacket end='w'?>\0\0";

        ParsedMetadata parsed = ScreenshotMetadataReader.parseXmpXml(attrXmp);
        assertTrue(parsed.hasMinecraftMetadata());
        assertEquals("Edited In Lightroom", parsed.worldName());
        assertEquals("Overworld", parsed.dimension());
        assertEquals("Cherry Grove", parsed.biome());
        assertEquals("x: 10, y: 90, z: 20", parsed.coordinates());
        assertEquals("Hard", parsed.difficulty());
        assertEquals("99.00 d", parsed.daysPlayed());
        assertEquals("14.50 d", parsed.worldAge());
        assertEquals("26.2", parsed.minecraftVersion());
        assertEquals("Photographer", parsed.playerName());
        assertEquals("spectator", parsed.gameMode());
    }

    // =====================================================
    // Corrupt, Truncated, Malformed, XXE, and Missing Files
    // =====================================================

    @Test
    @Timeout(2)
    void testCorruptChunkLengthsDoNotHangOrThrow() throws Exception {
        int[] badLengths = { -12, Integer.MIN_VALUE, Integer.MAX_VALUE, 999_999 };
        for (int i = 0; i < badLengths.length; i++) {
            Path corruptPath = tempDir.resolve("corrupt_" + i + ".png");
            Files.write(corruptPath, createCorruptChunkPng(badLengths[i], "iTXt"));

            MetadataReadResult result = ScreenshotMetadataReader.read(corruptPath);
            assertNotNull(result);
            assertFalse(result.hasMetadata());
        }
    }

    @Test
    void testTruncatedAndNonPngFilesFailSoftly() throws Exception {
        Path truncatedSig = tempDir.resolve("short.png");
        Files.write(truncatedSig, new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        MetadataReadResult shortResult = ScreenshotMetadataReader.read(truncatedSig);
        assertFalse(shortResult.hasXmpChunk());
        assertFalse(shortResult.hasMetadata());
        assertFalse(shortResult.fileInfo().hasDimensions());
        assertEquals(4, shortResult.fileInfo().fileSizeBytes());

        Path textFile = tempDir.resolve("not_a_png.png");
        Files.writeString(textFile, "This is definitely not a PNG file");
        MetadataReadResult textResult = ScreenshotMetadataReader.read(textFile);
        assertFalse(textResult.hasXmpChunk());
        assertFalse(textResult.hasMetadata());
        assertFalse(textResult.fileInfo().hasDimensions());
    }

    @Test
    void testMalformedXmlFailsSoftly() throws Exception {
        Path pngPath = tempDir.resolve("malformed_xml.png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePngHeaderAndIhdr(baos, 320, 240);
        writeItxtChunk(baos, ScreenshotMetadataReader.XMP_KEYWORD, "<x:xmpmeta><broken></x:xmpmeta>", false);
        writeChunk(baos, "IDAT", minimalIdatData());
        writeChunk(baos, "IEND", new byte[0]);
        Files.write(pngPath, baos.toByteArray());

        MetadataReadResult result = ScreenshotMetadataReader.read(pngPath);
        assertTrue(result.hasXmpChunk());
        assertFalse(result.hasMetadata());
        assertEquals(320, result.fileInfo().imageWidth());
        assertEquals(240, result.fileInfo().imageHeight());
    }

    @Test
    void testXxeAttackPayloadIsRejected() throws Exception {
        Path secretFile = tempDir.resolve("secret.txt");
        Files.writeString(secretFile, "TOP_SECRET_VALUE");

        String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE xmp [\n"
                + "  <!ENTITY xxe SYSTEM \"" + secretFile.toUri() + "\">\n"
                + "]>\n"
                + "<x:xmpmeta xmlns:x='adobe:ns:meta/'>\n"
                + "  <rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>\n"
                + "    <rdf:Description xmlns:mc='http://milezerosoftware.com/mc/1.0/'>\n"
                + "      <mc:WorldTitle>&xxe;</mc:WorldTitle>\n"
                + "    </rdf:Description>\n"
                + "  </rdf:RDF>\n"
                + "</x:xmpmeta>";

        ParsedMetadata parsed = ScreenshotMetadataReader.parseXmpXml(xxePayload);
        assertFalse(parsed.hasMinecraftMetadata(), "XXE DOCTYPE payload must be rejected");
        assertNull(parsed.worldName());
    }

    @Test
    void testMissingFileNullAndDirectoryFailSoftly() {
        MetadataReadResult missing = ScreenshotMetadataReader.read(tempDir.resolve("does_not_exist.png"));
        assertFalse(missing.hasXmpChunk());
        assertFalse(missing.hasMetadata());
        assertEquals(-1L, missing.fileInfo().fileSizeBytes());

        MetadataReadResult nullResult = ScreenshotMetadataReader.read(null);
        assertFalse(nullResult.hasMetadata());

        MetadataReadResult dirResult = ScreenshotMetadataReader.read(tempDir);
        assertFalse(dirResult.hasMetadata());
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private void invokeWriteMetadata(File file, MetadataHandler.ScreenshotMetadata metadata) throws Exception {
        Method writeMetadata = MetadataHandler.class.getDeclaredMethod(
                "writeMetadata", File.class, MetadataHandler.ScreenshotMetadata.class);
        writeMetadata.setAccessible(true);
        writeMetadata.invoke(null, file, metadata);
    }

    private void invokeWritePngWithXmp(byte[] pngData, File output, String xmpXml) throws Exception {
        Method writePngWithXmp = MetadataHandler.class.getDeclaredMethod(
                "writePngWithXmp", byte[].class, File.class, String.class);
        writePngWithXmp.setAccessible(true);
        writePngWithXmp.invoke(null, pngData, output, xmpXml);
    }

    private byte[] createMinimalPng(int width, int height) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePngHeaderAndIhdr(baos, width, height);
        writeChunk(baos, "IDAT", minimalIdatData());
        writeChunk(baos, "IEND", new byte[0]);
        return baos.toByteArray();
    }

    private void writePngHeaderAndIhdr(ByteArrayOutputStream baos, int width, int height) throws IOException {
        baos.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
        byte[] ihdrData = new byte[] {
                (byte) ((width >> 24) & 0xFF),
                (byte) ((width >> 16) & 0xFF),
                (byte) ((width >> 8) & 0xFF),
                (byte) (width & 0xFF),
                (byte) ((height >> 24) & 0xFF),
                (byte) ((height >> 16) & 0xFF),
                (byte) ((height >> 8) & 0xFF),
                (byte) (height & 0xFF),
                8, 2, 0, 0, 0
        };
        writeChunk(baos, "IHDR", ihdrData);
    }

    private byte[] minimalIdatData() {
        return new byte[] {
                0x78, (byte) 0x9C, 0x62, (byte) 0xF8,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00,
                0x05, (byte) 0xFE, 0x02, (byte) 0xFE
        };
    }

    private void writeItxtChunk(ByteArrayOutputStream baos, String keyword, String text, boolean compress)
            throws IOException {
        writeCustomItxtChunk(baos, keyword, compress ? 1 : 0, 0, "", "", text, compress);
    }

    private void writeCustomItxtChunk(
            ByteArrayOutputStream baos,
            String keyword,
            int compressionFlag,
            int compressionMethod,
            String languageTag,
            String translatedKeyword,
            String text,
            boolean compressPayload) throws IOException {
        ByteArrayOutputStream chunkData = new ByteArrayOutputStream();
        chunkData.write(keyword.getBytes(StandardCharsets.ISO_8859_1));
        chunkData.write(0); // null separator
        chunkData.write(compressionFlag);
        chunkData.write(compressionMethod);
        chunkData.write(languageTag.getBytes(StandardCharsets.ISO_8859_1));
        chunkData.write(0);
        chunkData.write(translatedKeyword.getBytes(StandardCharsets.UTF_8));
        chunkData.write(0);

        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        if (compressPayload) {
            ByteArrayOutputStream deflated = new ByteArrayOutputStream();
            try (DeflaterOutputStream dos = new DeflaterOutputStream(deflated)) {
                dos.write(utf8);
            }
            chunkData.write(deflated.toByteArray());
        } else {
            chunkData.write(utf8);
        }

        writeChunk(baos, "iTXt", chunkData.toByteArray());
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private byte[] createCorruptChunkPng(int declaredLength, String chunkType) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
        baos.write((declaredLength >> 24) & 0xFF);
        baos.write((declaredLength >> 16) & 0xFF);
        baos.write((declaredLength >> 8) & 0xFF);
        baos.write(declaredLength & 0xFF);
        baos.write(chunkType.getBytes(StandardCharsets.ISO_8859_1));
        baos.write(new byte[16]);
        return baos.toByteArray();
    }

    private void writeChunk(ByteArrayOutputStream baos, String type, byte[] data) throws IOException {
        baos.write((data.length >> 24) & 0xFF);
        baos.write((data.length >> 16) & 0xFF);
        baos.write((data.length >> 8) & 0xFF);
        baos.write(data.length & 0xFF);
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        baos.write(typeBytes);
        baos.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long crcValue = crc.getValue();
        baos.write((int) ((crcValue >> 24) & 0xFF));
        baos.write((int) ((crcValue >> 16) & 0xFF));
        baos.write((int) ((crcValue >> 8) & 0xFF));
        baos.write((int) (crcValue & 0xFF));
    }
}
