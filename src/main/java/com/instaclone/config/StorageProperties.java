package com.instaclone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String publicBaseUrl,
        boolean autoCreateBucket) {

    /** True when the url points at an object this app itself uploaded, not an arbitrary external URL. */
    public boolean isOwnedUrl(String url) {
        return url != null && url.startsWith(publicBaseUrl + "/" + bucket + "/");
    }
}
