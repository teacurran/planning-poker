package com.scrumpoker.worker;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.Set;

/**
 * Quarkus test profile for ExportJobIntegrationTest.
 * <p>
 * Configures test environment with:
 * <ul>
 *   <li>Mocked S3 client (via {@link MockS3Producer})</li>
 *   <li>Test-specific S3 bucket name</li>
 *   <li>Reduced signed URL expiration for testing (1 hour instead of 7 days)</li>
 *   <li>INFO-level logging for export-related classes</li>
 * </ul>
 * </p>
 *
 * <p><strong>Infrastructure:</strong></p>
 * <ul>
 *   <li>PostgreSQL: Auto-started via Quarkus Dev Services (Testcontainers)</li>
 *   <li>Redis: Auto-started via Quarkus Dev Services (Testcontainers)</li>
 *   <li>S3: Mocked via {@link MockS3Producer} (no real AWS calls)</li>
 * </ul>
 */
public class ExportJobTestProfile implements QuarkusTestProfile {

    /**
     * Override configuration properties for test environment.
     * <p>
     * Sets test-specific values for S3 bucket name and presigned URL expiration.
     * </p>
     *
     * @return Map of configuration property overrides
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Use test bucket name (not a real S3 bucket since we're using mocks)
            "s3.bucket-name", "test-exports-bucket",

            // Reduce signed URL expiration to 1 hour for testing
            "export.signed-url-expiration", "3600",

            // Enable INFO logging for export-related classes
            "quarkus.log.category.\"com.scrumpoker.worker\".level", "INFO",
            "quarkus.log.category.\"com.scrumpoker.integration.s3\".level", "INFO"
        );
    }

    /**
     * Enable CDI alternatives for this test profile.
     * <p>
     * Enables {@link MockS3Producer} which provides mocked S3Client and S3Presigner beans.
     * These mocks replace the real AWS SDK clients to avoid making actual S3 calls.
     * </p>
     *
     * @return Set of CDI alternative classes to enable
     */
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(MockS3Producer.class);
    }
}
