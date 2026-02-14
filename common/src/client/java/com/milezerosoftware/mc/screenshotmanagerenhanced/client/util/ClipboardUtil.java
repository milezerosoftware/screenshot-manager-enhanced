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
            Image image = ImageIO.read(imagePath.toFile());
            if (image == null)
                return false;

            ImageTransferable selection = new ImageTransferable(image);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            return true;
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
