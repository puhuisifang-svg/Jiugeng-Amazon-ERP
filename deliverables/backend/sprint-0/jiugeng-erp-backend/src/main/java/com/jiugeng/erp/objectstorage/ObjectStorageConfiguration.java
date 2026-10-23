package com.jiugeng.erp.objectstorage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class ObjectStorageConfiguration {
    @Bean(destroyMethod = "close")
    PrivateObjectStorage privateObjectStorage(Environment env) {
        String prefix = env.getProperty("app.storage.prefix", "erp/");
        String provider = env.getProperty("app.storage.provider", "local");
        if ("local".equals(provider)) return new PrivateObjectStorage(null, "", prefix);
        if (!"oss".equals(provider)) throw new IllegalStateException("Unknown storage provider");
        try {
            String endpoint = env.getRequiredProperty("app.storage.endpoint");
            String region = env.getRequiredProperty("app.storage.region");
            String bucket = env.getRequiredProperty("app.storage.bucket");
            if (!endpoint.matches("https://oss-[a-z0-9-]+\\.aliyuncs\\.com")
                    || !region.matches("[a-z0-9-]+") || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]"))
                throw new IllegalArgumentException();
            Path file = Path.of(env.getRequiredProperty("app.storage.credentials-file"));
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > 4096)
                throw new IllegalArgumentException();
            var permissions = Files.getPosixFilePermissions(file);
            if (permissions.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")))
                throw new IllegalArgumentException();
            String text = Files.readString(file);
            var id = field(text, "accessKeyId");
            var secret = field(text, "accessKeySecret");
            var config = new ClientBuilderConfiguration();
            config.setSignatureVersion(SignVersion.V4);
            config.setConnectionTimeout(10000);
            config.setSocketTimeout(30000);
            config.setMaxErrorRetry(2);
            var client = OSSClientBuilder.create().endpoint(endpoint).region(region)
                    .credentialsProvider(new DefaultCredentialProvider(id, secret))
                    .clientConfiguration(config).build();
            return new PrivateObjectStorage(client, bucket, prefix);
        } catch (Exception error) {
            // Deliberately omit the cause: parser/SDK errors may contain credential input.
            throw new IllegalStateException("OSS configuration invalid; check private credential file and storage settings");
        }
    }

    private String field(String text, String name) {
        var matcher = Pattern.compile("(?im)^\\s*" + name + "\\s*[:=：]?\\s+(\\S+)\\s*$").matcher(text);
        if (!matcher.find()) throw new IllegalArgumentException();
        return matcher.group(1);
    }
}
