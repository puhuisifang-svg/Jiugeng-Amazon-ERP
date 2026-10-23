package com.jiugeng.erp.objectstorage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Private binary storage. Callers must authorize access before using this API.
 * No SDK error bodies, credentials or signed URLs are exposed to callers.
 */
public class PrivateObjectStorage implements AutoCloseable {
    private final OSS client;
    private final String bucket;
    private final String prefix;
    private static final int MAX_BYTES = 64 * 1024 * 1024;

    public PrivateObjectStorage(OSS client, String bucket, String prefix) {
        if (prefix == null || !prefix.matches("[a-zA-Z0-9][a-zA-Z0-9/_-]{0,100}/"))
            throw new IllegalArgumentException("Invalid object storage prefix");
        this.client = client;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    public boolean enabled() { return client != null; }

    private String key(String category, String id) {
        if (category == null || !category.matches("[a-z][a-z0-9-]{0,40}")
                || id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,150}")
                || id.contains(".."))
            throw new IllegalArgumentException("Invalid object storage key");
        return prefix + category + "/" + id;
    }

    public void put(String category, String id, byte[] bytes) throws IOException {
        String key = key(category, id);
        if (!enabled()) return;
        if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Invalid object size");
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("application/octet-stream");
        metadata.setHeader("x-oss-object-acl", "private");
        try {
            client.putObject(bucket, key, new ByteArrayInputStream(bytes), metadata);
        } catch (RuntimeException error) {
            throw new IOException("Object storage upload failed");
        }
    }

    /** Only a genuine missing key permits legacy fallback; permission/network errors fail closed. */
    public Optional<byte[]> read(String category, String id) throws IOException {
        String key = key(category, id);
        if (!enabled()) return Optional.empty();
        try (var object = client.getObject(bucket, key)) {
            if (object.getObjectMetadata().getContentLength() > MAX_BYTES)
                throw new IOException("Stored object too large");
            byte[] bytes = object.getObjectContent().readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Stored object too large");
            return Optional.of(bytes);
        } catch (OSSException error) {
            if ("NoSuchKey".equals(error.getErrorCode())) return Optional.empty();
            throw new IOException("Object storage download failed");
        } catch (RuntimeException error) {
            throw new IOException("Object storage download failed");
        }
    }

    public void delete(String category, String id) throws IOException {
        String key = key(category, id);
        if (!enabled()) return;
        try { client.deleteObject(bucket, key); }
        catch (RuntimeException error) { throw new IOException("Object storage delete failed"); }
    }

    public byte[] readOrLegacy(String category, String id, byte[] legacy) {
        try { return read(category, id).orElse(legacy); }
        catch (IOException error) { throw new IllegalStateException("文件存储暂时不可用，请稍后重试"); }
    }

    public void putDocument(String category, String id, byte[] bytes) {
        try { put(category, id, bytes); }
        catch (IOException error) { throw new IllegalStateException("文件存储暂时不可用，请稍后重试"); }
    }

    @Override public void close() { if (client != null) client.shutdown(); }
}
