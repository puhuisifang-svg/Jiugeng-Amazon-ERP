package com.jiugeng.erp.importexport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalImportFileStore implements ImportFileStore {
    private final Path root;
    LocalImportFileStore(@Value("${app.imports.staging-dir}") String directory) {
        try {
            root = Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IllegalStateException("Import staging directory cannot be symlink");
            setPermissions(root, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (IOException exception) { throw new IllegalStateException("Cannot initialize import staging", exception); }
    }
    @Override public String store(InputStream input) throws IOException {
        return store(input, ".xlsx");
    }
    @Override public String storeResult(InputStream input) throws IOException {
        return store(input, ".csv");
    }
    private String store(InputStream input, String extension) throws IOException {
        String key = UUID.randomUUID() + extension;
        Path target = resolve(key);
        Files.copy(input, target);
        setPermissions(target, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        return key;
    }
    @Override public InputStream read(String key) throws IOException {
        Path file = resolve(key);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))
            throw new IOException("Stored import file missing");
        return Files.newInputStream(file);
    }
    @Override public void delete(String key) { try { Files.deleteIfExists(resolve(key)); } catch (IOException ignored) {} }

    CleanupResult cleanup(Set<String> protectedKeys, Instant cutoff) {
        int deleted = 0;
        List<String> failures = new ArrayList<>();
        try (var entries = Files.list(root)) {
            for (Path candidate : entries.toList()) {
                String key = candidate.getFileName().toString();
                if (!key.matches("[0-9a-fA-F-]{36}\\.(?:xlsx|csv)") || protectedKeys.contains(key)) continue;
                try {
                    if (Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (Files.getLastModifiedTime(candidate, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)
                            && Files.deleteIfExists(candidate)) deleted++;
                } catch (IOException exception) {
                    failures.add(key);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot scan import staging", exception);
        }
        return new CleanupResult(deleted, List.copyOf(failures));
    }
    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(?:xlsx|csv)")) throw new IllegalArgumentException("Invalid storage key");
        Path value = root.resolve(key).normalize();
        if (!value.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return value;
    }
    private void setPermissions(Path path, Set<PosixFilePermission> permissions) {
        try { Files.setPosixFilePermissions(path, permissions); } catch (UnsupportedOperationException | IOException ignored) {}
    }

    record CleanupResult(int deletedFiles, List<String> failedKeys) {}
}
