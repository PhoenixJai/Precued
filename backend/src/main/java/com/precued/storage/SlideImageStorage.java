package com.precued.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

/**
 * Thin wrapper over the R2 S3Client for slide images. Object keys are
 * internal storage identifiers only (see ShareSlide.imageUrl) — never
 * returned to a browser directly. See ShareSlideImageService for why: this
 * project proxies image bytes through the backend rather than issuing
 * public/presigned R2 URLs, so every fetch goes through the same
 * VisibilityEngine check as everything else, with no separate
 * signed-URL-TTL revocation-lag window.
 *
 * The {@code S3Client} parameter is {@code @Lazy}: without it, this bean
 * being an eagerly-created singleton would force r2Client's real
 * construction at application startup regardless of that bean's own
 * {@code @Lazy} annotation (a hard constructor dependency of an eager bean
 * still resolves eagerly — {@code @Lazy} on the producer alone only skips
 * *unused* singletons). A blank R2 account-id (see StorageConfig's own
 * Javadoc) would then fail application boot entirely over a
 * presentations-only dependency. With {@code @Lazy} here, real construction
 * (and any failure from missing config) is deferred to the first actual
 * upload/download call.
 */
@Component
public class SlideImageStorage {

    private final S3Client s3Client;
    private final String bucketName;

    public SlideImageStorage(
            @Lazy S3Client s3Client, @Value("${precued.storage.r2.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public void upload(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
    }

    public byte[] download(String key) {
        try (ResponseInputStream<GetObjectResponse> object =
                s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build())) {
            return object.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read slide image " + key + " from storage", e);
        }
    }
}
