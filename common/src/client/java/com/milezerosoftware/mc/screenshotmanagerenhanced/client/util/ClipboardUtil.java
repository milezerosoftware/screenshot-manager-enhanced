package com.milezerosoftware.mc.screenshotmanagerenhanced.client.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.nio.file.Path;

public class ClipboardUtil {

    public static boolean copyImageToClipboard(Path imagePath) {
        try {
            // 1. Try AWT (Standard Java)
            // Determine if we are on Mac to try native approach if AWT fails or as
            // preferred
            String os = System.getProperty("os.name").toLowerCase();
            boolean isMac = os.contains("mac");

            if (!isMac) {
                // Try AWT first for non-Mac
                return copyAWT(imagePath);
            } else {
                // On Mac, try AWT but fallback to osascript immediately if it fails
                if (copyAWT(imagePath))
                    return true;
                return copyMacOsascript(imagePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAWT(Path imagePath) {
        try {
            Image image = ImageIO.read(imagePath.toFile());
            if (image == null)
                return false;

            ImageTransferable selection = new ImageTransferable(image);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            return true;
        } catch (Exception e) {
            // AWT failed (headless, etc)
            return false;
        }
    }

    private static boolean copyMacOsascript(Path imagePath) {
        try {
            // Use AppleScript to set clipboard to the image file content
            // "set the clipboard to (read (POSIX file "/path/...") as JPEG picture)" -
            // simpler often works with "as record" or just "read" for file
            // Reliable way: "set the clipboard to (read (POSIX file "...") as TIFF
            // picture)"

            ProcessBuilder pb = new ProcessBuilder("osascript", "-e",
                    "set the clipboard to (read (POSIX file \"" + imagePath.toAbsolutePath().toString()
                            + "\") as TIFF picture)");
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static class ImageTransferable implements Transferable {
        private final Image image;

        public ImageTransferable(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
