package com.scrumpoker.domain.room;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for calculating voting statistics and consensus detection.
 * Implements variance-based consensus algorithm for Fibonacci deck estimation.
 */
public class ConsensusCalculator {

    /**
     * Variance threshold for consensus detection.
     * Variance < 2.0 indicates consensus for Fibonacci deck (1, 2, 3, 5, 8, 13).
     */
    private static final double VARIANCE_THRESHOLD = 2.0;

    /**
     * Set of numeric card values supported by the Fibonacci deck.
     */
    private static final List<String> NUMERIC_CARDS = List.of("0", "1", "2", "3", "5", "8", "13", "21", "34", "55", "89");

    /**
     * Calculates whether consensus has been reached based on vote variance.
     * <p>
     * Consensus Algorithm:
     * 1. If any votes are non-numeric (?, ∞, ☕), consensus is FALSE
     * 2. If all votes are the same value, consensus is TRUE (variance = 0)
     * 3. Calculate variance: σ² = Σ(xi - μ)² / n
     * 4. If variance < 2.0, consensus is TRUE; otherwise FALSE
     * </p>
     *
     * @param votes List of votes to analyze
     * @return true if consensus reached, false otherwise
     */
    public static boolean calculateConsensus(List<Vote> votes) {
        if (votes == null || votes.isEmpty()) {
            return false;
        }

        // Check if all votes are numeric
        boolean allNumeric = votes.stream()
                .allMatch(vote -> isNumericCardValue(vote.cardValue));

        if (!allNumeric) {
            return false; // Automatic failure if any non-numeric votes
        }

        // If all votes are the same, consensus is reached
        String firstValue = votes.get(0).cardValue;
        boolean allSame = votes.stream()
                .allMatch(vote -> vote.cardValue.equals(firstValue));

        if (allSame) {
            return true; // Consensus with variance = 0
        }

        // Calculate variance for numeric votes
        List<Double> numericValues = votes.stream()
                .map(vote -> Double.parseDouble(vote.cardValue))
                .collect(Collectors.toList());

        double variance = calculateVariance(numericValues);
        return variance < VARIANCE_THRESHOLD;
    }

    /**
     * Calculates the average of numeric votes.
     * Non-numeric votes are excluded from the calculation.
     *
     * @param votes List of votes to analyze
     * @return Average value rounded to 2 decimal places, or null if no numeric votes
     */
    public static BigDecimal calculateAverage(List<Vote> votes) {
        if (votes == null || votes.isEmpty()) {
            return null;
        }

        List<Double> numericValues = votes.stream()
                .filter(vote -> isNumericCardValue(vote.cardValue))
                .map(vote -> Double.parseDouble(vote.cardValue))
                .collect(Collectors.toList());

        if (numericValues.isEmpty()) {
            return null; // No numeric votes
        }

        double sum = numericValues.stream().mapToDouble(Double::doubleValue).sum();
        double average = sum / numericValues.size();

        return BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the median vote value.
     * <p>
     * For numeric votes: sorts values and takes the middle value (or average of two middle values if even count).
     * For non-numeric votes: returns the most common value, or "mixed" if no clear majority.
     * </p>
     *
     * @param votes List of votes to analyze
     * @return Median value as a string (e.g., "5", "6.5", "?", "mixed")
     */
    public static String calculateMedian(List<Vote> votes) {
        if (votes == null || votes.isEmpty()) {
            return null;
        }

        // Check if all votes are numeric
        boolean allNumeric = votes.stream()
                .allMatch(vote -> isNumericCardValue(vote.cardValue));

        if (allNumeric) {
            // Calculate numeric median
            List<Double> numericValues = votes.stream()
                    .map(vote -> Double.parseDouble(vote.cardValue))
                    .sorted()
                    .collect(Collectors.toList());

            int size = numericValues.size();
            if (size % 2 == 1) {
                // Odd number of votes: return middle value
                return String.valueOf(numericValues.get(size / 2).intValue());
            } else {
                // Even number of votes: return average of two middle values
                double middle1 = numericValues.get(size / 2 - 1);
                double middle2 = numericValues.get(size / 2);
                double median = (middle1 + middle2) / 2.0;

                // Format as integer if whole number, otherwise with decimal
                if (median == Math.floor(median)) {
                    return String.valueOf((int) median);
                } else {
                    return String.valueOf(BigDecimal.valueOf(median).setScale(1, RoundingMode.HALF_UP));
                }
            }
        } else {
            // Mixed numeric and non-numeric votes: find most common value
            return votes.stream()
                    .collect(Collectors.groupingBy(vote -> vote.cardValue, Collectors.counting()))
                    .entrySet().stream()
                    .max((e1, e2) -> e1.getValue().compareTo(e2.getValue()))
                    .map(entry -> {
                        // Check if there's a clear majority (more than 50%)
                        long count = entry.getValue();
                        if (count > votes.size() / 2) {
                            return entry.getKey();
                        } else {
                            return "mixed";
                        }
                    })
                    .orElse("mixed");
        }
    }

    /**
     * Checks if a card value is numeric (can be parsed as a number).
     *
     * @param cardValue The card value to check
     * @return true if numeric, false otherwise
     */
    private static boolean isNumericCardValue(String cardValue) {
        if (cardValue == null) {
            return false;
        }
        return NUMERIC_CARDS.contains(cardValue);
    }

    /**
     * Calculates the statistical variance of a list of numeric values.
     * Variance formula: σ² = Σ(xi - μ)² / n
     *
     * @param values List of numeric values
     * @return The variance
     */
    private static double calculateVariance(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }

        // Calculate mean
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Calculate sum of squared differences
        double sumSquaredDiff = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .sum();

        // Return variance
        return sumSquaredDiff / values.size();
    }
}
