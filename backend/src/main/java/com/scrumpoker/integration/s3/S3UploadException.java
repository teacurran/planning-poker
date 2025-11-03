package com.scrumpoker.integration.s3;

/**
 * Exception thrown when S3 file upload or presigned URL generation fails.
 * <p>
 * Wraps AWS SDK exceptions for cleaner exception handling in the service layer.
 * </p>
 *
 * <p><strong>Common Causes:</strong></p>
 * <ul>
 *   <li>Network connectivity issues (transient - retry)</li>
 *   <li>Invalid AWS credentials (permanent - fail job)</li>
 *   <li>Bucket does not exist (permanent - fail job)</li>
 *   <li>Insufficient S3 permissions (permanent - fail job)</li>
 *   <li>S3 throttling (transient - retry with backoff)</li>
 * </ul>
 */
public class S3UploadException extends RuntimeException {

    /**
     * Constructs a new S3 upload exception with the specified detail message.
     *
     * @param message The detail message
     */
    public S3UploadException(final String message) {
        super(message);
    }

    /**
     * Constructs a new S3 upload exception with the specified detail message and cause.
     *
     * @param message The detail message
     * @param cause The underlying cause
     */
    public S3UploadException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
