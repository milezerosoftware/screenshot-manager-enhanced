package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.InflaterInputStream;

/**
 * Pure-Java streaming PNG metadata reader.
 * <p>
 * Scans PNG chunks using {@link FileChannel} without loading image pixel payloads into memory,
 * extracting image dimensions from {@code IHDR} and embedded XMP XML from {@code iTXt} chunks
 * with keyword {@code XML:com.adobe.xmp} (supporting both uncompressed and zlib-compressed chunks,
 * placed either before or after {@code IDAT}).
 */
public final class ScreenshotMetadataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger("screenshot-manager-enhanced");

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    public static final String XMP_KEYWORD = "XML:com.adobe.xmp";
    public static final String NS_MC = "http://milezerosoftware.com/mc/1.0/";
    public static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    public static final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    static final int MAX_ITXT_BYTES = 1024 * 1024; // 1 MiB cap for raw & decompressed XMP payload
    private static final int MAX_CHUNKS = 10_000;

    private static final ErrorHandler SILENT_ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) {
            // Suppress stderr noise
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    };

    private ScreenshotMetadataReader() {
    }

    /**
     * Basic file-level properties extracted alongside PNG header dimensions.
     */
    public record FileInfo(int imageWidth, int imageHeight, long fileSizeBytes, long lastModifiedMillis) {
        public static final FileInfo EMPTY = new FileInfo(0, 0, -1L, -1L);

        public boolean hasDimensions() {
            return imageWidth > 0 && imageHeight > 0;
        }
    }

    /**
     * Parsed Minecraft XMP metadata fields.
     */
    public record ParsedMetadata(
            String worldName,
            String dimension,
            String biome,
            String coordinates,
            String difficulty,
            String daysPlayed,
            String worldAge,
            String minecraftVersion,
            String playerName,
            String gameMode,
            String description) {

        public static final ParsedMetadata EMPTY = new ParsedMetadata(
                null, null, null, null, null, null, null, null, null, null, null);

        public boolean hasMinecraftMetadata() {
            return isPresent(worldName)
                    || isPresent(dimension)
                    || isPresent(biome)
                    || isPresent(coordinates)
                    || isPresent(difficulty)
                    || isPresent(daysPlayed)
                    || isPresent(worldAge)
                    || isPresent(minecraftVersion)
                    || isPresent(playerName)
                    || isPresent(gameMode);
        }

        public boolean isEmpty() {
            return !hasMinecraftMetadata() && !isPresent(description);
        }
    }

    /**
     * Combined result of reading a screenshot file's attributes, PNG header, and embedded XMP.
     */
    public record MetadataReadResult(FileInfo fileInfo, ParsedMetadata metadata, boolean hasXmpChunk) {
        public static MetadataReadResult empty() {
            return new MetadataReadResult(FileInfo.EMPTY, ParsedMetadata.EMPTY, false);
        }

        public boolean hasMetadata() {
            return metadata != null && metadata.hasMinecraftMetadata();
        }
    }

    /**
     * Reads file info, PNG dimensions, and embedded Minecraft XMP metadata from {@code path}.
     * Fails softly on missing, truncated, non-PNG, or malformed files.
     */
    public static MetadataReadResult read(Path path) {
        if (path == null) {
            return MetadataReadResult.empty();
        }

        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            LOGGER.debug("Unable to read file attributes for {}: {}", path, e.getMessage());
            return MetadataReadResult.empty();
        }

        if (!attrs.isRegularFile()) {
            return MetadataReadResult.empty();
        }

        long fileSize = attrs.size();
        long lastModifiedMillis = attrs.lastModifiedTime().toMillis();

        int imageWidth = 0;
        int imageHeight = 0;
        String xmpXml = null;

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long channelSize = channel.size();
            if (channelSize < PNG_SIGNATURE.length) {
                return new MetadataReadResult(
                        new FileInfo(0, 0, fileSize, lastModifiedMillis),
                        ParsedMetadata.EMPTY,
                        false);
            }

            ByteBuffer sigBuf = ByteBuffer.allocate(PNG_SIGNATURE.length);
            if (!readFully(channel, sigBuf) || !matchesSignature(sigBuf.array())) {
                return new MetadataReadResult(
                        new FileInfo(0, 0, fileSize, lastModifiedMillis),
                        ParsedMetadata.EMPTY,
                        false);
            }

            ByteBuffer headerBuf = ByteBuffer.allocate(8);
            long pos = PNG_SIGNATURE.length;
            int chunksRead = 0;

            while (pos + 8L <= channelSize && chunksRead < MAX_CHUNKS) {
                chunksRead++;
                channel.position(pos);
                headerBuf.clear();
                if (!readFully(channel, headerBuf)) {
                    break;
                }
                headerBuf.flip();

                int length = headerBuf.getInt(0);
                // PNG chunk lengths are unsigned 31-bit values (0..Integer.MAX_VALUE).
                // Negative ints indicate bit 31 set / corrupt data.
                if (length < 0) {
                    break;
                }

                long dataStart = pos + 8L;
                long nextPos = dataStart + (long) length + 4L; // payload + 4-byte CRC
                if (nextPos > channelSize || nextPos <= pos) {
                    break;
                }

                byte t0 = headerBuf.get(4);
                byte t1 = headerBuf.get(5);
                byte t2 = headerBuf.get(6);
                byte t3 = headerBuf.get(7);

                if (isChunkType(t0, t1, t2, t3, 'I', 'H', 'D', 'R')) {
                    if (imageWidth == 0 && imageHeight == 0 && length >= 8) {
                        ByteBuffer ihdrBuf = ByteBuffer.allocate(8);
                        if (readFully(channel, ihdrBuf)) {
                            ihdrBuf.flip();
                            int w = ihdrBuf.getInt(0);
                            int h = ihdrBuf.getInt(4);
                            if (w > 0 && h > 0) {
                                imageWidth = w;
                                imageHeight = h;
                            }
                        }
                    }
                } else if (isChunkType(t0, t1, t2, t3, 'i', 'T', 'X', 't')) {
                    if (xmpXml == null && length > 0 && length <= MAX_ITXT_BYTES) {
                        ByteBuffer dataBuf = ByteBuffer.allocate(length);
                        if (readFully(channel, dataBuf)) {
                            xmpXml = extractXmpFromItxt(dataBuf.array());
                        }
                    }
                } else if (isChunkType(t0, t1, t2, t3, 'I', 'E', 'N', 'D')) {
                    break;
                }

                if (xmpXml != null && imageWidth > 0 && imageHeight > 0) {
                    break;
                }

                pos = nextPos;
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to read PNG metadata from {}: {}", path, e.getMessage());
        }

        FileInfo fileInfo = new FileInfo(imageWidth, imageHeight, fileSize, lastModifiedMillis);
        if (xmpXml == null) {
            return new MetadataReadResult(fileInfo, ParsedMetadata.EMPTY, false);
        }

        ParsedMetadata parsed = parseXmpXml(xmpXml);
        return new MetadataReadResult(fileInfo, parsed, true);
    }

    /**
     * Parses an XMP XML string into a {@link ParsedMetadata} record using an XXE-hardened,
     * namespace-aware DOM parser. Supports both child elements ({@code <mc:...>}) and
     * {@code rdf:Description} attributes ({@code mc:...}).
     */
    public static ParsedMetadata parseXmpXml(String xmpXml) {
        if (xmpXml == null) {
            return ParsedMetadata.EMPTY;
        }

        String cleaned = cleanXmlInput(xmpXml);
        if (cleaned.isEmpty()) {
            return ParsedMetadata.EMPTY;
        }

        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(SILENT_ERROR_HANDLER);

            Document doc = builder.parse(new InputSource(new StringReader(cleaned)));

            NodeList descriptions = doc.getElementsByTagNameNS(NS_RDF, "Description");

            String worldName = readMcProperty(doc, descriptions, "WorldTitle");
            String dimension = readMcProperty(doc, descriptions, "Dimension");
            String biome = readMcProperty(doc, descriptions, "Biome");
            String coordinates = readMcProperty(doc, descriptions, "Coordinates");
            String difficulty = readMcProperty(doc, descriptions, "Difficulty");
            String daysPlayed = readMcProperty(doc, descriptions, "Days");
            String worldAge = readMcProperty(doc, descriptions, "WorldAge");
            String minecraftVersion = readMcProperty(doc, descriptions, "Version");
            String playerName = readMcProperty(doc, descriptions, "PlayerName");
            String gameMode = readMcProperty(doc, descriptions, "GameMode");
            String description = readDcDescription(doc, descriptions);

            return new ParsedMetadata(
                    worldName,
                    dimension,
                    biome,
                    coordinates,
                    difficulty,
                    daysPlayed,
                    worldAge,
                    minecraftVersion,
                    playerName,
                    gameMode,
                    description);
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XMP metadata XML: {}", e.getMessage());
            return ParsedMetadata.EMPTY;
        }
    }

    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException ignored) {
            // Optional JAXP property on non-Xerces implementations
        }
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Optional JAXP property on non-Xerces implementations
        }
        return factory;
    }

    private static String readMcProperty(Document doc, NodeList descriptions, String localName) {
        // 1. Check rdf:Description child elements and attributes
        if (descriptions != null) {
            for (int i = 0; i < descriptions.getLength(); i++) {
                Node node = descriptions.item(i);
                if (node instanceof Element desc) {
                    String fromChildren = findElementText(desc.getElementsByTagNameNS(NS_MC, localName));
                    if (fromChildren != null) {
                        return fromChildren;
                    }
                    String fromAttr = normalizeText(desc.getAttributeNS(NS_MC, localName));
                    if (fromAttr != null) {
                        return fromAttr;
                    }
                }
            }
        }

        // 2. Fallback: document-wide lookup in the mc namespace
        return findElementText(doc.getElementsByTagNameNS(NS_MC, localName));
    }

    private static String readDcDescription(Document doc, NodeList descriptions) {
        NodeList descNodes = doc.getElementsByTagNameNS(NS_DC, "description");
        for (int i = 0; i < descNodes.getLength(); i++) {
            Node node = descNodes.item(i);
            if (node instanceof Element elem) {
                String liText = findElementText(elem.getElementsByTagNameNS(NS_RDF, "li"));
                if (liText != null) {
                    return liText;
                }
                String directText = normalizeText(elem.getTextContent());
                if (directText != null) {
                    return directText;
                }
            }
        }
        if (descriptions != null) {
            for (int i = 0; i < descriptions.getLength(); i++) {
                Node node = descriptions.item(i);
                if (node instanceof Element desc) {
                    String attr = normalizeText(desc.getAttributeNS(NS_DC, "description"));
                    if (attr != null) {
                        return attr;
                    }
                }
            }
        }
        return null;
    }

    private static String findElementText(NodeList nodes) {
        if (nodes == null) {
            return null;
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = normalizeText(nodes.item(i).getTextContent());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String extractXmpFromItxt(byte[] data) {
        int keywordEnd = indexOfZero(data, 0);
        if (keywordEnd <= 0 || keywordEnd + 3 >= data.length) {
            return null;
        }

        String keyword = new String(data, 0, keywordEnd, StandardCharsets.ISO_8859_1);
        if (!XMP_KEYWORD.equals(keyword)) {
            return null;
        }

        int compressionFlag = data[keywordEnd + 1] & 0xFF;
        int compressionMethod = data[keywordEnd + 2] & 0xFF;

        int langTagEnd = indexOfZero(data, keywordEnd + 3);
        if (langTagEnd < 0) {
            return null;
        }

        int translatedKeywordEnd = indexOfZero(data, langTagEnd + 1);
        if (translatedKeywordEnd < 0) {
            return null;
        }

        int textOffset = translatedKeywordEnd + 1;
        int textLength = data.length - textOffset;
        if (textLength <= 0) {
            return null;
        }

        if (compressionFlag == 0) {
            return new String(data, textOffset, textLength, StandardCharsets.UTF_8);
        }

        if (compressionFlag == 1 && compressionMethod == 0) {
            byte[] inflated = inflateWithCap(data, textOffset, textLength, MAX_ITXT_BYTES);
            if (inflated == null) {
                return null;
            }
            return new String(inflated, StandardCharsets.UTF_8);
        }

        return null;
    }

    private static byte[] inflateWithCap(byte[] source, int offset, int length, int maxBytes) {
        try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(source, offset, length));
             ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(length * 2, 4096))) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = iis.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    LOGGER.debug("Compressed XMP iTXt chunk exceeded {} byte cap", maxBytes);
                    return null;
                }
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.debug("Failed to inflate compressed XMP iTXt chunk: {}", e.getMessage());
            return null;
        }
    }

    private static String cleanXmlInput(String raw) {
        int start = 0;
        int end = raw.length();
        while (start < end && (raw.charAt(start) == '\uFEFF' || raw.charAt(start) == '\0' || Character.isWhitespace(raw.charAt(start)))) {
            start++;
        }
        while (end > start && (raw.charAt(end - 1) == '\0' || Character.isWhitespace(raw.charAt(end - 1)))) {
            end--;
        }
        return raw.substring(start, end);
    }

    private static int indexOfZero(byte[] data, int fromIndex) {
        for (int i = fromIndex; i < data.length; i++) {
            if (data[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer);
            if (n < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesSignature(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isChunkType(byte b0, byte b1, byte b2, byte b3, char c0, char c1, char c2, char c3) {
        return b0 == (byte) c0 && b1 == (byte) c1 && b2 == (byte) c2 && b3 == (byte) c3;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
