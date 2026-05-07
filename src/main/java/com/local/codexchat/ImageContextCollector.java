package com.local.codexchat;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class ImageContextCollector {
    private static final long MAX_IMAGE_BYTES = 20L * 1024L * 1024L;

    private ImageContextCollector() {
    }

    static PromptContext chooseImage(Project project) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("选择图片")
                .withDescription("支持 PNG、JPG、JPEG、WEBP、GIF。图片会作为 Codex 原生图片附件发送。");

        VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
        if (file == null) {
            return null;
        }

        return fromFile(new File(file.getPath()), "Image file");
    }

    static PromptContext fromClipboard() {
        try {
            Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (transferable == null || !transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return null;
            }

            Image image = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
            if (image == null) {
                return null;
            }

            BufferedImage buffered = toBufferedImage(image);
            File temp = File.createTempFile("codex-chat-clipboard-", ".png");
            temp.deleteOnExit();
            ImageIO.write(buffered, "png", temp);
            return fromFile(temp, "Clipboard image");
        } catch (Exception exception) {
            return null;
        }
    }

    private static PromptContext fromFile(File file, String source) {
        if (file == null || !file.isFile()) {
            return null;
        }

        String extension = extension(file.getName());
        if (!isSupported(extension)) {
            return new PromptContext(
                    PromptContext.Type.IMAGE,
                    "Unsupported image: " + file.getName(),
                    "Unsupported image format: " + extension + "\nPath: " + file.getAbsolutePath(),
                    null
            );
        }

        long size = file.length();
        if (size > MAX_IMAGE_BYTES) {
            return new PromptContext(
                    PromptContext.Type.IMAGE,
                    "Image too large: " + file.getName(),
                    "Image exceeds " + MAX_IMAGE_BYTES + " bytes.\nPath: " + file.getAbsolutePath()
                            + "\nSize: " + size + " bytes",
                    null
            );
        }

        BufferedImage image = null;
        try {
            image = ImageIO.read(file);
        } catch (IOException ignored) {
            // Some formats may still be accepted by Codex even if ImageIO cannot decode metadata.
        }

        StringBuilder body = new StringBuilder();
        body.append("Source: ").append(source).append('\n');
        body.append("Path: ").append(file.getAbsolutePath()).append('\n');
        body.append("Format: ").append(extension.toUpperCase(Locale.ROOT)).append('\n');
        body.append("Size: ").append(size).append(" bytes").append('\n');
        if (image != null) {
            body.append("Dimensions: ").append(image.getWidth()).append("x").append(image.getHeight()).append('\n');
        } else {
            body.append("Dimensions: unavailable").append('\n');
        }
        body.append("SHA-256: ").append(sha256(file)).append('\n');
        body.append('\n');
        body.append("This image is attached through `codex exec --image`; analyze visual content directly.");

        return new PromptContext(
                PromptContext.Type.IMAGE,
                "Image: " + file.getName(),
                body.toString(),
                file.getAbsolutePath()
        );
    }

    private static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }

        BufferedImage buffered = new BufferedImage(
                Math.max(1, image.getWidth(null)),
                Math.max(1, image.getHeight(null)),
                BufferedImage.TYPE_INT_ARGB
        );
        java.awt.Graphics2D graphics = buffered.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return buffered;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isSupported(String extension) {
        return extension.equals("png")
                || extension.equals("jpg")
                || extension.equals("jpeg")
                || extension.equals("webp")
                || extension.equals("gif");
    }

    private static String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file.toPath()));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }
}
