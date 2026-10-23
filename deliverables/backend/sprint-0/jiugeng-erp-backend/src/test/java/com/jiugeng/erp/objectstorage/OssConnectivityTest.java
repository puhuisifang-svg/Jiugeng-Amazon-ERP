package com.jiugeng.erp.objectstorage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

/** Opt-in synthetic probe only; never uses customer files or prints configuration. */
@EnabledIfEnvironmentVariable(named = "ERP_OSS_PROBE", matches = "true")
class OssConnectivityTest {
    @Test void privateV4UploadDownloadAndCleanup() throws Exception {
        var env = new MockEnvironment().withProperty("app.storage.provider", "oss")
            .withProperty("app.storage.endpoint", System.getenv("ERP_OSS_ENDPOINT"))
            .withProperty("app.storage.region", System.getenv("ERP_OSS_REGION"))
            .withProperty("app.storage.bucket", System.getenv("ERP_OSS_BUCKET"))
            .withProperty("app.storage.credentials-file", System.getenv("ERP_OSS_CREDENTIALS_FILE"));
        try (var storage = new ObjectStorageConfiguration().privateObjectStorage(env)) {
            String id = UUID.randomUUID() + ".txt";
            byte[] data = "ERP-076 synthetic V4 verification".getBytes(StandardCharsets.UTF_8);
            try {
                storage.put("diagnostics", id, data);
                assertThat(storage.read("diagnostics", id).orElseThrow()).isEqualTo(data);
                var endpoint = java.net.URI.create(System.getenv("ERP_OSS_ENDPOINT"));
                var uri = java.net.URI.create("https://" + System.getenv("ERP_OSS_BUCKET") + "."
                    + endpoint.getHost() + "/erp/diagnostics/" + id);
                try (var http = java.net.http.HttpClient.newHttpClient()) {
                    var response = http.send(java.net.http.HttpRequest.newBuilder(uri)
                        .timeout(java.time.Duration.ofSeconds(20)).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding());
                    assertThat(response.statusCode()).isEqualTo(403);
                }
            } finally { storage.delete("diagnostics", id); }
            assertThat(storage.read("diagnostics", id)).isEmpty();
        }
    }
}
