package com.smart.vision.core.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Data Transfer Object for Hybrid Search Parameters.
 * Contains the parameters related to hybrid search requests.
 */
@Data
@Builder
public class HybridSearchParamDTO {

    /**
     * The query vector for similarity search, represented as a list of floats.
     */
    private List<Float> queryVector;

    /**
     * Graph triples associated with the search query.
     */
    private List<GraphTripleDTO> graphTriples;

    /**
     * Keyword used in the search.
     */
    private String keyword;

    /**
     * The maximum number of results to return.
     */
    private Integer limit;

    /**
     * Pagination parameter for search results, used to retrieve the next set.
     */
    private List<Object> searchAfter;

    /**
     * The top-K results to retrieve based on relevance or similarity.
     */
    private Integer topK;

    /**
     * The similarity threshold or score for the query.
     */
    private Float similarity;
}
