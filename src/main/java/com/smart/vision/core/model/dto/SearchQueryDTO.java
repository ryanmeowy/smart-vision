package com.smart.vision.core.model.dto;

import lombok.Data;

import java.util.List;

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
     * topK
     */
    private Integer topK;
    /**
     * page number
     */
    private Integer pageNo;
    /**
     * minimum similarity threshold
     */
    private Float similarity;
    /**
     * whether to enable OCR hybrid search
     */
    private boolean enableOcr;
    /**
     * search strategy type
     * @see com.smart.vision.core.model.enums.StrategyTypeEnum
     */
    private String searchType;

    /**
     * Maximum number of results to return
     */
    private Integer limit;

    /**
     * Search cursor for pagination
     */
    private List<Object> searchAfter;

}