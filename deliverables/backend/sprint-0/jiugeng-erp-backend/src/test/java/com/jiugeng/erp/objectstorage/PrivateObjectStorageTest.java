package com.jiugeng.erp.objectstorage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class PrivateObjectStorageTest {
    private final OSS client = mock(OSS.class);
    private final PrivateObjectStorage storage = new PrivateObjectStorage(client, "test-bucket", "erp/");

    @Test void localModeKeepsLegacyWithoutRemoteCalls() throws Exception {
        var local = new PrivateObjectStorage(null, "", "erp/");
        assertThat(local.enabled()).isFalse();
        local.put("images", "sample.png", new byte[]{1});
        assertThat(local.readOrLegacy("images", "sample.png", new byte[]{2})).containsExactly(2);
        local.delete("images", "sample.png");
    }

    @Test void uploadsPrivateObjectWithBoundedStableKey() throws Exception {
        storage.put("images", "sample.png", new byte[]{1,2});
        var metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(eq("test-bucket"), eq("erp/images/sample.png"), any(ByteArrayInputStream.class), metadata.capture());
        assertThat(metadata.getValue().getContentLength()).isEqualTo(2);
        assertThat(metadata.getValue().getRawMetadata().get("x-oss-object-acl")).isEqualTo("private");
    }

    @Test void readsRemoteAndClosesStream() throws Exception {
        var input = spy(new ByteArrayInputStream(new byte[]{1,2}));
        var object = new OSSObject();
        object.setObjectContent(input);
        when(client.getObject("test-bucket","erp/images/sample.png")).thenReturn(object);
        assertThat(storage.read("images","sample.png").orElseThrow()).containsExactly(1,2);
        verify(input).close();
    }

    @Test void missingObjectAllowsLegacyButPermissionFailureDoesNot() throws Exception {
        var missing = new OSSException("redacted") {
            @Override public String getErrorCode() { return "NoSuchKey"; }
        };
        doThrow(missing).when(client).getObject(anyString(),anyString());
        assertThat(storage.read("images","sample.png")).isEmpty();
        var denied = new OSSException("redacted") {
            @Override public String getErrorCode() { return "AccessDenied"; }
        };
        doThrow(denied).when(client).getObject(anyString(),anyString());
        assertThatThrownBy(()->storage.read("images","sample.png"))
            .isInstanceOf(IOException.class).hasMessage("Object storage download failed").hasNoCause();
    }

    @Test void rejectsTraversalBeforeAccessingCloud() {
        assertThatThrownBy(()->storage.read("images","../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->storage.delete("../images","sample.png")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    @Test void uploadFailureIsNotSilentlyAccepted() {
        when(client.putObject(anyString(),anyString(),any(ByteArrayInputStream.class),any(ObjectMetadata.class)))
            .thenThrow(new RuntimeException("sensitive"));
        assertThatThrownBy(()->storage.put("images","sample.png",new byte[]{1}))
            .isInstanceOf(IOException.class).hasNoCause().hasMessage("Object storage upload failed");
    }
}
