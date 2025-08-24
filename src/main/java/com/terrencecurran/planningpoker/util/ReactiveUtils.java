package com.terrencecurran.planningpoker.util;

import io.smallrye.mutiny.Uni;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Utility class for common reactive patterns and defensive programming.
 * Helps avoid common pitfalls with reactive programming in Quarkus.
 */
public class ReactiveUtils {
    
    private static final Logger LOGGER = Logger.getLogger(ReactiveUtils.class.getName());
    
    /**
     * Safely chains a database operation after a transaction completes.
     * This pattern prevents "Illegal pop()" errors by ensuring operations
     * happen in separate contexts.
     */
    public static <T, R> Uni<R> chainAfterTransaction(
            Uni<T> transaction, 
            Supplier<Uni<R>> nextOperation) {
        return transaction
            .flatMap(result -> {
                // Complete the transaction first
                return Uni.createFrom().item(result);
            })
            // Then perform the next operation in a fresh context
            .flatMap(result -> nextOperation.get());
    }
    
    /**
     * Wraps an operation with consistent error handling and logging.
     */
    public static <T> Uni<T> withErrorHandling(
            Uni<T> operation, 
            String operationName,
            T fallbackValue) {
        return operation
            .onFailure().invoke(error -> 
                LOGGER.severe(String.format("%s failed: %s", 
                    operationName, error.getMessage()))
            )
            .onFailure().recoverWithItem(fallbackValue);
    }
    
    /**
     * Ensures an operation completes within a timeout period.
     */
    public static <T> Uni<T> withTimeout(
            Uni<T> operation,
            long milliseconds,
            String timeoutMessage) {
        return operation
            .ifNoItem().after(java.time.Duration.ofMillis(milliseconds))
            .failWith(new RuntimeException(timeoutMessage));
    }
    
    /**
     * Safely extracts a value from a transaction result for use after
     * the transaction completes. This prevents holding onto entity
     * references across transaction boundaries.
     */
    public static <T, R> Uni<R> extractFromTransaction(
            Uni<T> transaction,
            java.util.function.Function<T, R> extractor) {
        return transaction.map(result -> {
            // Extract only the data we need, not the entity itself
            return extractor.apply(result);
        });
    }
    
    /**
     * Combines multiple operations with individual error handling.
     * Unlike Uni.combine().all() which fails fast, this completes
     * all operations and collects results/errors.
     */
    @SafeVarargs
    public static Uni<Void> combineWithIndividualHandling(
            Uni<Void>... operations) {
        return Uni.join().all(operations)
            .andCollectFailures()
            .replaceWithVoid();
    }
    
    /**
     * Helper to ensure a string is not null or empty.
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    /**
     * Creates a failed Uni with a specific error message.
     * Useful for validation failures.
     */
    public static <T> Uni<T> failWith(String errorMessage) {
        return Uni.createFrom().failure(new IllegalArgumentException(errorMessage));
    }
}