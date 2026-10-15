package com.jiugeng.erp.dashboard;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalDashboardImageStore implements DashboardImageStore {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_DIMENSION = 4096;
    static final long MAX_PIXELS = 16_000_000L;
    private static final int THUMBNAIL_BOUND = 320;
    private final Path root;

    LocalDashboardImageStore(@Value("${app.dashboard.image-dir}") String directory) {
        try {
            root = Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IllegalStateException("Dashboard image directory cannot be symlink");
            setPermissions(root, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize dashboard image directory", exception);
        }
    }

    @Override
    public StoredImage store(byte[] source) throws IOException {
        if (source == null || source.length == 0 || source.length > MAX_BYTES) {
            throw new DashboardException(413, "DASHBOARD_IMAGE_TOO_LARGE", "图片必须存在且不得超过 5 MB");
        }
        Decoded decoded = decode(source);
        byte[] normalized = png(decoded.image());
        byte[] thumbnail = png(thumbnail(decoded.image()));
        String id = UUID.randomUUID().toString();
        String originalKey = id + "-original.png";
        String thumbnailKey = id + "-thumbnail.png";
        try {
            writeAtomically(originalKey, normalized);
            writeAtomically(thumbnailKey, thumbnail);
        } catch (IOException exception) {
            delete(originalKey);
            delete(thumbnailKey);
            throw exception;
        }
        return new StoredImage(originalKey, thumbnailKey, "image/png", decoded.sourceMediaType(), source.length,
                sha256(source), decoded.image().getWidth(), decoded.image().getHeight());
    }

    @Override
    public byte[] read(String key) throws IOException {
        Path file = resolve(key);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException("Dashboard image file is missing");
        }
        return Files.readAllBytes(file);
    }

    @Override
    public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); } catch (IOException | IllegalArgumentException ignored) { }
    }

    private Decoded decode(byte[] source) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (stream == null) throw invalid();
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String mediaType = switch (format) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> throw invalid();
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new DashboardException(400, "DASHBOARD_IMAGE_DIMENSIONS_INVALID",
                            "图片宽高不得超过 4096 像素且总像素不得超过 1600 万");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw invalid();
                return new Decoded(image, mediaType);
            } finally {
                reader.dispose();
            }
        } catch (DashboardException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private BufferedImage thumbnail(BufferedImage source) {
        double scale = Math.min(1d, (double) THUMBNAIL_BOUND / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
        return output.toByteArray();
    }

    private void writeAtomically(String key, byte[] bytes) throws IOException {
        Path target = resolve(key);
        Path temporary = root.resolve(key + ".tmp").normalize();
        if (!temporary.startsWith(root)) throw new IOException("Invalid image path");
        try {
            Files.write(temporary, bytes);
            setPermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}-(?:original|thumbnail)\\.png")) {
            throw new IllegalArgumentException("Invalid dashboard image key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid dashboard image key");
        return path;
    }

    private DashboardException invalid() {
        return new DashboardException(400, "DASHBOARD_IMAGE_INVALID", "仅支持可正常解码的 JPG、PNG 或 WebP 图片");
    }

    private String sha256(byte[] source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private void setPermissions(Path path, Set<PosixFilePermission> permissions) {
        try { Files.setPosixFilePermissions(path, permissions); }
        catch (UnsupportedOperationException | IOException ignored) { }
    }

    private record Decoded(BufferedImage image, String sourceMediaType) {}
}
