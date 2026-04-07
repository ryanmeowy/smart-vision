package com.smart.vision.core.search.interfaces.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "keyword cannot be empty")
    @Size(max = 50, message = "keyword length cannot exceed 50")
    private String keyword;
    /**
     * topK
     */
    @Min(value = 1, message = "topK must be greater than 0")
    @Max(value = 200, message = "topK cannot exceed 200")
    private Integer topK;
    /**
     * page number
     */
    private Integer pageNo;
    /**
     * Deprecated for retrieval flow.
     * Kept for backward compatibility of callers that still send the field.
     */
    @Deprecated
    private Float similarity;
    /**
     * whether to enable OCR hybrid search
     */
    private Boolean enableOcr = true;
    /**
     * search strategy type
     * @see com.smart.vision.core.search.domain.model.StrategyTypeEnum
     */
    @Pattern(regexp = "[0-3]", message = "searchType must be one of 0,1,2,3")
    private String searchType;

    /**
     * Maximum number of results to return
     */
    @Min(value = 1, message = "limit must be greater than 0")
    @Max(value = 200, message = "limit cannot exceed 200")
    private Integer limit;

    /**
     * Search cursor for pagination
     */
    private List<Object> searchAfter;

}
