
package com.smart.vision.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VectorUtil {

    /**
     * 对向量进行 L2 归一化
     * 公式：x_new = x / sqrt(sum(x^2))
     */
    public static List<Float> l2Normalize(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 计算平方和 (使用 double 防止精度丢失)
        double sumSquares = 0.0;
        for (Float val : vector) {
            if (val != null) {
                sumSquares += (val * val);
            }
        }

        // 2. 计算模长 (L2 Norm)
        double magnitude = Math.sqrt(sumSquares);

        // 3. 防御性判断：如果模长为 0 (全零向量)，直接返回原向量，避免除以 0 得到 NaN
        if (magnitude < 1e-10) { // 一个极小的数
            return vector;
        }

        // 4. 归一化：每个元素除以模长
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