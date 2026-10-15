package com.jiugeng.erp.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDashboardImageStoreTest {
    private static final byte[] ONE_PIXEL_WEBP = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");

    @Test
    void decodesWebpAndStoresNormalizedProtectedPng(@TempDir java.nio.file.Path directory) throws Exception {
        LocalDashboardImageStore store = new LocalDashboardImageStore(directory.toString());

        DashboardImageStore.StoredImage image = store.store(ONE_PIXEL_WEBP);

        assertThat(image.sourceMediaType()).isEqualTo("image/webp");
        assertThat(image.storedMediaType()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(1);
        assertThat(image.height()).isEqualTo(1);
        assertThat(store.read(image.originalKey())).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(Files.isRegularFile(directory.resolve(image.thumbnailKey()))).isTrue();
    }

    @Test
    void rejectsPayloadLargerThanFiveMegabytes(@TempDir java.nio.file.Path directory) {
        LocalDashboardImageStore store = new LocalDashboardImageStore(directory.toString());

        assertThatThrownBy(() -> store.store(new byte[LocalDashboardImageStore.MAX_BYTES + 1]))
                .isInstanceOf(DashboardException.class)
                .extracting(exception -> ((DashboardException) exception).code())
                .isEqualTo("DASHBOARD_IMAGE_TOO_LARGE");
    }
}
