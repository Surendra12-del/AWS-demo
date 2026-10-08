package com.example.aws.demo.storage;

import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Wraps Amazon S3 operations (upload / download / delete) used to store product images.
 */
@Service
public class StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public StorageService(S3Client s3Client, @Value("${aws.s3.bucket:}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    private void requireBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException(
                    "S3 bucket is not configured. Set aws.s3.bucket (or AWS_S3_BUCKET env var).");
        }
    }

    /**
     * Uploads a file to S3 and returns the generated object key.
     */
    public String upload(MultipartFile file) throws IOException {
        requireBucket();
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String key = "products/" + UUID.randomUUID() + "-" + original;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        return key;
    }

    /**
     * Downloads an object's bytes from S3.
     */
    public DownloadedObject download(String key) {
        requireBucket();
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(request);
        return new DownloadedObject(bytes.asByteArray(), bytes.response().contentType());
    }

    /**
     * Deletes an object from S3. No-op if key is null/blank.
     */
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        requireBucket();
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** Bytes + content type of a downloaded S3 object. */
    public record DownloadedObject(byte[] bytes, String contentType) {
    }
}
