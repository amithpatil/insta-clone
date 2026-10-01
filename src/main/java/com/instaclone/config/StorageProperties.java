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

    /** True when the url points at an object this specific user uploaded via /posts/upload-url —
     * not just any object in the app's bucket, which would let a user reference media another user
     * uploaded (object keys are "posts/{userId}/..." per StorageService.createUploadUrl). */
    public boolean isOwnedUrl(String url, Long userId) {
        return url != null && url.startsWith(publicBaseUrl + "/" + bucket + "/posts/" + userId + "/");
    }
}
