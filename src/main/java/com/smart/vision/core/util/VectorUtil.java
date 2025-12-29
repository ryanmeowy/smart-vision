
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
}