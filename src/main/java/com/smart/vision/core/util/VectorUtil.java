
package com.smart.vision.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for vector operations
 * Provides methods for vector normalization and other mathematical operations
 *
 * @author Ryan
 * @since 2025/12/26
 */

public class VectorUtil {

    /**
     * Perform L2 normalization on a vector
     * Formula: x_new = x / sqrt(sum(x^2))
     */
    public static List<Float> l2Normalize(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Calculate the sum of squares (use double to prevent precision loss)
        double sumSquares = 0.0;
        for (Float val : vector) {
            if (val != null) {
                sumSquares += (val * val);
            }
        }

        // 2. Calculate the magnitude (L2 Norm)
        double magnitude = Math.sqrt(sumSquares);

        // 3. Defensive check: if the magnitude is 0 (all-zero vector), return the original vector directly to avoid dividing by 0 and getting NaN
        if (magnitude < 1e-10) { // A very small number
            return vector;
        }

        // 4. Normalize: divide each element by the magnitude
        List<Float> normalizedVector = new ArrayList<>(vector.size());
        for (Float val : vector) {
            if (val != null) {
                normalizedVector.add((float) (val / magnitude));
            } else {
                normalizedVector.add(0.0f);
            }
        }

        return normalizedVector;
    }

    /**
     * Calculate L2 norm (vector magnitude).
     */
    public static double l2Norm(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return 0d;
        }
        double sumSquares = 0d;
        for (Float value : vector) {
            if (value != null) {
                sumSquares += value * value;
            }
        }
        return Math.sqrt(sumSquares);
    }

    /**
     * Calculate cosine similarity in range [-1, 1].
     */
    public static double cosineSimilarity(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0d;
        }

        double dot = 0d;
        double leftNormSquare = 0d;
        double rightNormSquare = 0d;
        for (int i = 0; i < left.size(); i++) {
            float l = left.get(i) == null ? 0f : left.get(i);
            float r = right.get(i) == null ? 0f : right.get(i);
            dot += (double) l * r;
            leftNormSquare += (double) l * l;
            rightNormSquare += (double) r * r;
        }
        if (leftNormSquare <= 0d || rightNormSquare <= 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(leftNormSquare) * Math.sqrt(rightNormSquare));
    }
}
