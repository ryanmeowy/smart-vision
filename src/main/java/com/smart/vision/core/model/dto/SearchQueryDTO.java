
package com.smart.vision.core.model.dto;

import lombok.Data;

@Data
public class SearchQueryDTO {
    /**
     * 搜索关键词
     */
    private String text;
    /**
     * 分页大小
     */
    private Integer limit;
    /**
     * 最小相似度阈值
     */
    private Float minScore = 0.6f;
    /**
     * 是否开启 OCR 混合检索开关
     */
    private boolean enableOcr;
}