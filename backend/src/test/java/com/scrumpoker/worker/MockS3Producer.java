package com.scrumpoker.worker;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.annotation.Priority;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CDI producer for mocked S3 clients used in integration tests.
 * <p>
 * This class produces mock implementations of {@link S3Client} and {@link S3Presigner}
 * to avoid making real AWS S3 calls during testing. The mocks are configured with
 * realistic responses that simulate successful S3 operations.
 * </p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 *   // In test profile, enable this alternative:
 *   public Set&lt;Class&lt;?&gt;&gt; getEnabledAlternatives() {
 *       return Set.of(MockS3Producer.class);
 *   }
 * </pre>
 *
 * <p><strong>Mock Behavior:</strong></p>
 * <ul>
 *   <li>S3Client.putObject() returns success response with random ETag</li>
 *   <li>S3Presigner.presignGetObject() returns valid-looking presigned URL</li>
 *   <li>For failure tests, reconfigure mock to throw exceptions</li>
 * </ul>
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MockS3Producer {

    /**
     * Produces a mocked S3Client configured for successful uploads.
     * <p>
     * The mock returns a {@link PutObjectResponse} with a random ETag,
     * simulating a successful S3 upload without actually calling AWS.
     * </p>
     *
     * @return Mocked S3Client instance
     */
    @Produces
    @ApplicationScoped
    public S3Client createMockS3Client() {
        Log.info("Creating mock S3Client for integration tests");

        S3Client mockClient = mock(S3Client.class);

        // Configure successful upload by default
        when(mockClient.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder()
                .eTag("test-etag-" + UUID.randomUUID())
                .build());

        return mockClient;
    }

    /**
     * Produces a mocked S3Presigner configured to return valid presigned URLs.
     * <p>
     * The mock returns a {@link PresignedGetObjectRequest} with a realistic-looking
     * presigned URL (https://test-bucket.s3.amazonaws.com/...).
     * </p>
     *
     * @return Mocked S3Presigner instance
     */
    @Produces
    @ApplicationScoped
    public S3Presigner createMockS3Presigner() {
        Log.info("Creating mock S3Presigner for integration tests");

        S3Presigner mockPresigner = mock(S3Presigner.class);

        try {
            URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/exports/test-file.csv?presigned=true");
            PresignedGetObjectRequest mockPresignedRequest = mock(PresignedGetObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(mockUrl);

            when(mockPresigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock presigned URL", e);
        }

        return mockPresigner;
    }
}
