package com.jiugeng.erp.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalImportFileStoreTest {
    @TempDir Path root;

    @Test void cleanupKeepsActiveRecentUnknownAndSymlinkFiles() throws Exception {
        LocalImportFileStore store = new LocalImportFileStore(root.toString());
        String active = store.store(new ByteArrayInputStream(new byte[]{1}));
        String expired = store.storeResult(new ByteArrayInputStream(new byte[]{2}));
        String recent = store.store(new ByteArrayInputStream(new byte[]{3}));
        Instant now = Instant.parse("2026-10-15T00:00:00Z");
        Files.setLastModifiedTime(root.resolve(active), FileTime.from(now.minusSeconds(900_000)));
        Files.setLastModifiedTime(root.resolve(expired), FileTime.from(now.minusSeconds(900_000)));
        Files.setLastModifiedTime(root.resolve(recent), FileTime.from(now.minusSeconds(60)));
        Files.writeString(root.resolve("operator-note.txt"), "keep");
        Path symlink = root.resolve("00000000-0000-0000-0000-000000000000.csv");
        try { Files.createSymbolicLink(symlink, root.resolve(expired)); } catch (UnsupportedOperationException ignored) {}

        var result = store.cleanup(Set.of(active), now.minusSeconds(604_800));

        assertThat(result.deletedFiles()).isEqualTo(1);
        assertThat(result.failedKeys()).isEmpty();
        assertThat(root.resolve(active)).exists();
        assertThat(root.resolve(expired)).doesNotExist();
        assertThat(root.resolve(recent)).exists();
        assertThat(root.resolve("operator-note.txt")).exists();
        if (Files.exists(symlink)) assertThat(Files.isSymbolicLink(symlink)).isTrue();
    }

    @Test void readAndDeleteRejectTraversalAndForeignNames() {
        LocalImportFileStore store = new LocalImportFileStore(root.toString());
        assertThatThrownBy(() -> store.read("../outside.xlsx")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete("not-a-storage-key.xlsx")).isInstanceOf(IllegalArgumentException.class);
    }
}
