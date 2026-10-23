package com.jiugeng.erp.objectstorage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ObjectStorageConfigurationTest {
    @Test void defaultsToLocalWithoutCredentials() {
        try (var storage = new ObjectStorageConfiguration().privateObjectStorage(new MockEnvironment())) {
            assertThat(storage.enabled()).isFalse();
        }
    }
    @Test void missingOssConfigurationFailsWithoutExposingInput() {
        var env = new MockEnvironment().withProperty("app.storage.provider","oss");
        assertThatThrownBy(()->new ObjectStorageConfiguration().privateObjectStorage(env))
            .isInstanceOf(IllegalStateException.class).hasNoCause()
            .hasMessage("OSS configuration invalid; check private credential file and storage settings");
    }
    @Test void typoDoesNotSilentlyFallBackToLocal() {
        var env = new MockEnvironment().withProperty("app.storage.provider","os");
        assertThatThrownBy(()->new ObjectStorageConfiguration().privateObjectStorage(env))
            .hasMessage("Unknown storage provider");
    }
}
