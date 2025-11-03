package com.scrumpoker.worker;

/**
 * Exception thrown when CSV or PDF export generation fails.
 * <p>
 * Wraps underlying exceptions (I/O errors, data access errors) for
 * cleaner exception handling in the export job processor.
 * </p>
 *
 * <p><strong>Common Causes:</strong></p>
 * <ul>
 *   <li>Database query failures (transient - retry)</li>
 *   <li>I/O errors during file generation (transient - retry)</li>
 *   <li>Invalid session data (permanent - fail job)</li>
 *   <li>Out of memory (permanent - fail job)</li>
 *   <li>PDF rendering errors (permanent - fail job)</li>
 * </ul>
 */
public class ExportGenerationException extends RuntimeException {

    /**
     * Constructs a new export generation exception with the specified detail message.
     *
     * @param message The detail message
     */
    public ExportGenerationException(final String message) {
        super(message);
    }

    /**
     * Constructs a new export generation exception with the specified detail message and cause.
     *
     * @param message The detail message
     * @param cause The underlying cause
     */
    public ExportGenerationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
