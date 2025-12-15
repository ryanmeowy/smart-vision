
package com.smart.vision.core.model.dto;

import lombok.Data;

/**
 * image search request model
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Data
public class SearchQueryDTO {
    /**
     * search keyword
     */
    private String keyword;
    /**
     * page size
     */
    private Integer limit;
    /**
     * minimum similarity threshold
     */
    private Float minScore = 0.6f;
    /**
     * whether to enable OCR hybrid search
     */
    private boolean enableOcr;

}