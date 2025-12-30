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
     * score mapping algorithm
     * Mapping rules:
     * 1. [0.0, 0.4) -> [0.0, 0.65) : Power function (Square), slow start, accelerated sprint in the later stage
     * 2. [0.4, 0.7) -> [0.65, 0.9) : Linear interpolation, smooth transition
     * 3. [0.7, +∞)  -> [0.9, 1.0] : Exponential decay (Asymptotic), infinitely approaching 1.0
     *
     * @param rawScore Original score (ES Score)
     * @return Double percentage from 0.0 to 0.99
     */
    public static double mapScoreToPercentage(Double rawScore) {
        if (rawScore == null || rawScore <= 0) {
            return 0;
        }

        double result;

        if (rawScore < 0.4) {
            // --- Segment 1: Slow -> Fast (Quadratic Curve) ---
            // Formula: Ratio * (x / Range)^2
            // Input 0.2 (halfway) -> Output 0.65 * 0.25 = 0.16 (far below halfway)
            // Input 0.39 (close to endpoint) -> Close to 0.65
            result = 0.65 * Math.pow(rawScore / 0.4, 2);
        
        } else if (rawScore < 0.7) {
            // --- Segment 2: Smooth rise (Linear) ---
            // Span: Input 0.3, Output 0.25
            // Slope: 0.25 / 0.3 ≈ 0.83
            result = 0.65 + ((rawScore - 0.4) / 0.3) * 0.25;
        
        } else {
            // --- Segment 3: Marginal decrease (Exponential Decay) ---
            // Formula: Max - (Max - Base) * e^(-k * dx)
            // Function characteristic: The larger x, the slower the growth, never exceeding 1.0
            // k=2.0 is an empirical coefficient controlling the saturation speed
            result = 1.0 - 0.1 * Math.exp(-2.0 * (rawScore - 0.7));
        }
        return result;
    }

    // --- Test validation ---
    public static void main(String[] args) {
        // 1. Low score segment test (slow start)
        test(0.1); // Expected: very low
        test(0.2);
        test(0.35); // Expected: rapid rise
        test(0.39); // Expected: close to 65%

        System.out.println("----------------");
        
        // 2. Middle score segment test (smooth)
        test(0.4);  // Exactly 65%
        test(0.55); // Middle value
        test(0.69); // Close to 90%

        System.out.println("----------------");

        // 3. High score segment test (marginal decrease)
        test(0.7);  // Exactly 90%
        test(0.8);  // 91%
        test(1.5);  // 98%
        test(3.0);  // 99%
        test(10.0); // 100% (extreme value)
    }

    private static void test(double score) {
        System.out.printf("Raw: %-5s -> Result: %d%%%n", score, (int) (mapScoreToPercentage(score) * 100));
    }
}