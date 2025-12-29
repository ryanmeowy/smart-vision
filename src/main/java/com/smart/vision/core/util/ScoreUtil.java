package com.smart.vision.core.util;

/**
 * Utility class for score formatting.
 * Provides methods to format raw scores into display scores based on predefined intervals.
 *
 * @author Ryan
 * @since 2025/12/29
 */
public class ScoreUtil {

    /**
     * Formats the raw score into a display score based on specific intervals.
     * Interval 1: [0.0, 0.5) -> 0% ~ 40%
     * Formula: y = (x / 0.5) * 40
     * Interval 2: [0.5, 0.7) -> 40% ~ 80%
     * Formula: y = 40 + ((x - 0.5) / 0.2) * 40
     * Interval 3: [0.7, +∞] -> 80% ~ 99%
     * Formula: y = 80 + ((x - 0.7) / 0.3) * 19
     * If the input exceeds 1.0, the score is capped at 99.0.
     *
     * @param rawScore The raw score to be formatted.
     * @return The formatted display score.
     */
    public static double formatDisplayScore(double rawScore) {
        // Defensive handling: scores less than 0 are directly set to zero
        if (rawScore < 0.0) {
            return 0.0;
        }

        // First interval: [0.0, 0.5) -> 0% ~ 40%
        // Formula: y = (x / 0.5) * 40
        if (rawScore < 0.5) {
            return (rawScore / 0.5) * 0.40;
        }

        // Second interval: [0.5, 0.7) -> 40% ~ 80%
        // Formula: y = 40 + ((x - 0.5) / 0.2) * 40
        if (rawScore < 0.7) {
            return 0.40 + ((rawScore - 0.5) / 0.2) * 0.40;
        }

        // Third interval: [0.7, +∞] -> 80% ~ 99%
        // Assume the normal full score is 1.0. Formula: y = 80 + ((x - 0.7) / 0.3) * 19
        // If the input exceeds 1.0 (e.g., due to boost), use min to forcibly cap it at 99.0
        double result = 0.80 + ((rawScore - 0.7) / 0.3) * 0.19;

        // Cap at 99.0
        return Math.min(result, 0.99);
    }
}