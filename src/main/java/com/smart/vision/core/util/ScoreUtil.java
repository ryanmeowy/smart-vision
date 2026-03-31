package com.smart.vision.core.util;
/**
 * Utility class for score formatting.
 *
 * @author Ryan
 * @since 2025/12/29
 */
public class ScoreUtil {

    /**
     * Clamp the raw ES score into [0, 1] as a lightweight display score.
     *
     * @param rawScore original score (ES score)
     * @return score in [0, 1]
     */
    public static double mapScoreToPercentage(Double rawScore) {
        if (rawScore == null || rawScore <= 0) {
            return 0;
        }
        return Math.min(1.0d, rawScore);
    }
}
