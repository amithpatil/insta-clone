package com.instaclone.post;

import com.instaclone.config.StorageProperties;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Presigned-upload flow: the client PUTs the file straight to storage using the returned URL,
 * then calls POST /posts referencing the resulting public URL. The API never touches the bytes.
 * Same code path works against SeaweedFS's S3 gateway (local) or Cloudflare R2 (production) —
 * only app.storage.* config changes.
 */
@Service
public class StorageService {

    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(10);

    private final S3Presigner presigner;
    private final StorageProperties props;

    public StorageService(S3Presigner presigner, StorageProperties props) {
        this.presigner = presigner;
        this.props = props;
    }

    public PresignedUploadResponse createUploadUrl(Long userId, String contentType) {
        String extension = contentType.substring(contentType.indexOf('/') + 1);
        String objectKey = "posts/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(putRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        // Derived from the same props.bucket() used to sign the PUT above, not an independently
        // configured value — otherwise the two can drift (e.g. a bucket rename) and publicUrl
        // silently points at the wrong object.
        String publicUrl = props.publicBaseUrl() + "/" + props.bucket() + "/" + objectKey;

        return new PresignedUploadResponse(presigned.url().toString(), objectKey, publicUrl);
    }
}
