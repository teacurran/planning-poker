package com.scrumpoker.integration.s3;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * AWS S3 integration adapter for uploading export files and generating signed URLs.
 * <p>
 * Provides methods for:
 * <ul>
 *   <li>Uploading CSV/PDF files to S3 bucket</li>
 *   <li>Generating time-limited presigned URLs for downloads (7-day expiration)</li>
 * </ul>
 * </p>
 *
 * <p><strong>S3 Object Key Structure:</strong></p>
 * <pre>
 *   exports/{sessionId}/{jobId}.{format}
 *   Example: exports/123e4567-e89b-12d3-a456-426614174000/abc-def-ghi.csv
 * </pre>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>
 * AWS SDK clients are blocking. This adapter wraps blocking calls with
 * {@code Uni.createFrom().item()} and executes them on worker thread pool
 * to avoid blocking the event loop.
 * </p>
 *
 * @see ExportJobProcessor
 */
@ApplicationScoped
public class S3Adapter {

    /**
     * S3 bucket name for export files.
     */
    @ConfigProperty(name = "s3.bucket-name")
    private String bucketName;

    /**
     * Signed URL expiration time in seconds (default: 7 days = 604800 seconds).
     */
    @ConfigProperty(name = "export.signed-url-expiration", defaultValue = "604800")
    private long signedUrlExpirationSeconds;

    /**
     * AWS S3 synchronous client (blocking).
     */
    @Inject
    private S3Client s3Client;

    /**
     * AWS S3 presigner for generating signed URLs.
     */
    @Inject
    private S3Presigner s3Presigner;

    /**
     * Uploads a file to S3 bucket and returns a presigned URL for download.
     * <p>
     * Object key format: {@code exports/{sessionId}/{jobId}.{format}}
     * </p>
     *
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>Network errors: Propagates to caller for retry logic</li>
     *   <li>Access denied: Throws S3UploadException</li>
     *   <li>Bucket not found: Throws S3UploadException</li>
     * </ul>
     *
     * @param sessionId The session UUID (used in object key path)
     * @param jobId The job UUID (used in object key filename)
     * @param format File format ("CSV" or "PDF")
     * @param fileContent File content as byte array
     * @return Uni containing the presigned download URL (valid for 7 days)
     * @throws S3UploadException if upload fails
     */
    public Uni<String> uploadFileAndGenerateUrl(
            final UUID sessionId,
            final UUID jobId,
            final String format,
            final byte[] fileContent) {

        if (sessionId == null || jobId == null || format == null || fileContent == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "sessionId, jobId, format, and fileContent cannot be null"));
        }

        // Build S3 object key: exports/{sessionId}/{jobId}.{format}
        final String objectKey = buildObjectKey(sessionId, jobId, format);

        // Determine content type based on format
        final String contentType = getContentType(format);

        Log.infof("Uploading export file to S3: bucket=%s, key=%s, size=%d bytes",
                bucketName, objectKey, fileContent.length);

        // Execute blocking S3 upload on worker pool
        return Uni.createFrom().item(() -> {
            try {
                // Create PutObject request
                final PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength((long) fileContent.length)
                        .build();

                // Upload file
                final PutObjectResponse response = s3Client.putObject(
                        putRequest,
                        RequestBody.fromInputStream(
                                new ByteArrayInputStream(fileContent),
                                fileContent.length));

                Log.infof("Successfully uploaded file to S3: etag=%s, key=%s",
                        response.eTag(), objectKey);

                // Generate presigned URL
                return generatePresignedUrl(objectKey);

            } catch (Exception e) {
                Log.errorf(e, "Failed to upload file to S3: key=%s", objectKey);
                throw new S3UploadException(
                        "Failed to upload export file to S3: " + e.getMessage(), e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Generates a presigned URL for downloading an S3 object.
     * <p>
     * URL is valid for the configured expiration time (default: 7 days).
     * No authentication required to access the URL during validity period.
     * </p>
     *
     * @param objectKey The S3 object key
     * @return Presigned URL string
     */
    private String generatePresignedUrl(final String objectKey) {
        try {
            final GetObjectPresignRequest presignRequest =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(signedUrlExpirationSeconds))
                            .getObjectRequest(req -> req
                                    .bucket(bucketName)
                                    .key(objectKey))
                            .build();

            final PresignedGetObjectRequest presignedRequest =
                    s3Presigner.presignGetObject(presignRequest);

            final String url = presignedRequest.url().toString();

            Log.infof("Generated presigned URL for S3 object: key=%s, expiration=%d seconds",
                    objectKey, signedUrlExpirationSeconds);

            return url;

        } catch (Exception e) {
            Log.errorf(e, "Failed to generate presigned URL for S3 object: key=%s", objectKey);
            throw new S3UploadException(
                    "Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the S3 object key for an export file.
     * <p>
     * Format: {@code exports/{sessionId}/{jobId}.{format}}
     * </p>
     *
     * @param sessionId The session UUID
     * @param jobId The job UUID
     * @param format File format (CSV or PDF)
     * @return S3 object key
     */
    private String buildObjectKey(final UUID sessionId, final UUID jobId, final String format) {
        return String.format("exports/%s/%s.%s",
                sessionId.toString(),
                jobId.toString(),
                format.toLowerCase());
    }

    /**
     * Determines the MIME content type based on file format.
     *
     * @param format File format (CSV or PDF)
     * @return MIME content type
     */
    private String getContentType(final String format) {
        return switch (format.toUpperCase()) {
            case "CSV" -> "text/csv";
            case "PDF" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
