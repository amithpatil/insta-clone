package com.instaclone.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Wired against SeaweedFS's S3 gateway locally (endpoint/credentials in application.yml under
 * app.storage). Swapping to real Cloudflare R2 later is a config change only — R2 speaks the same
 * S3 API.
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                // chunkedEncodingEnabled(false): the AWS SDK's default streaming/chunked payload
                // signing for PutObject isn't accepted by SeaweedFS's S3 gateway ("Signed request
                // requires setting up SeaweedFS S3 authentication") — only matters for the direct
                // server-side putObject calls the media transcode worker makes; the client-side
                // presigned-PUT upload flow (StorageService) is unaffected either way.
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        // publicBaseUrl, not endpoint: a presigned URL's host is part of what's signed, and it's
        // handed to an external client (curl, a browser) to PUT/GET directly — it must be an
        // address that client can reach. That's the same value as endpoint when the app runs on
        // the host (application.yml's default), but the two diverge once the app runs inside the
        // compose network (application-docker.yml): endpoint becomes the internal "seaweedfs" DNS
        // name for the app's own calls, while publicBaseUrl stays the host-reachable address.
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.publicBaseUrl()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public CommandLineRunner ensureBucketExists(S3Client s3Client, StorageProperties props) {
        return args -> {
            if (!props.autoCreateBucket()) {
                return;
            }
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
                log.info("Created storage bucket '{}'", props.bucket());
            } catch (BucketAlreadyOwnedByYouException e) {
                log.debug("Storage bucket '{}' already exists", props.bucket());
            } catch (Exception e) {
                log.warn("Could not verify/create storage bucket '{}': {}", props.bucket(), e.getMessage());
            }
        };
    }
}
