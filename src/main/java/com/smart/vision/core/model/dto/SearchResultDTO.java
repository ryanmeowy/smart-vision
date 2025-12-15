
package com.smart.vision.core.model.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class SearchResultDTO implements Serializable {

    /**
     * 图片ID (ES Doc ID)
     */
    private String id;

    /**
     * 图片访问地址 (OSS URL)
     */
    private String url;

    /**
     * 匹配得分 (0.0 - 1.0)，分数越高越相关
     */
    private Double score;

    /**
     * 图片中包含的 OCR 文字 (如果有)
     */
    private String ocrText;

    /**
     * 高亮摘要 (可选，用于展示命中的文字片段)
     */
    private String highlight;

    /**
     * 额外元数据 (宽、高、大小等)
     */
    private Object metadata;
}