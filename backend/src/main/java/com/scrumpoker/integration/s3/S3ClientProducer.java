package com.scrumpoker.integration.s3;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Optional;

/**
 * CDI producer for AWS S3 clients.
 * Provides configured S3Client and S3Presigner beans for dependency injection.
 */
@ApplicationScoped
public class S3ClientProducer {

    @ConfigProperty(name = "aws.s3.endpoint-override")
    Optional<String> endpointOverride;

    @ConfigProperty(name = "aws.s3.region", defaultValue = "us-east-1")
    String region;

    @ConfigProperty(name = "aws.access-key-id")
    Optional<String> accessKeyId;

    @ConfigProperty(name = "aws.secret-access-key")
    Optional<String> secretAccessKey;

    /**
     * Produces S3Client bean for object storage operations.
     * <p>
     * Configuration:
     * - aws.s3.endpoint-override: Custom S3 endpoint (e.g., LocalStack for testing)
     * - aws.s3.region: AWS region (default: us-east-1)
     * - aws.access-key-id: AWS access key
     * - aws.secret-access-key: AWS secret key
     * </p>
     *
     * @return Configured S3Client
     */
    @Produces
    @ApplicationScoped
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region));

        // Use custom endpoint for testing (e.g., LocalStack)
        endpointOverride.ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));

        // Use static credentials if provided (for testing/development)
        if (accessKeyId.isPresent() && secretAccessKey.isPresent()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId.get(), secretAccessKey.get())));
        }

        return builder.build();
    }

    /**
     * Produces S3Presigner bean for generating presigned URLs.
     * <p>
     * Used to create time-limited download URLs for exported reports.
     * </p>
     *
     * @return Configured S3Presigner
     */
    @Produces
    @ApplicationScoped
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region));

        // Use custom endpoint for testing (e.g., LocalStack)
        endpointOverride.ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));

        // Use static credentials if provided (for testing/development)
        if (accessKeyId.isPresent() && secretAccessKey.isPresent()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId.get(), secretAccessKey.get())));
        }

        return builder.build();
    }
}
