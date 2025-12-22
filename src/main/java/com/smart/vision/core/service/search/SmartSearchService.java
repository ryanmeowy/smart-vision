package com.smart.vision.core.service.search;

import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;

import java.util.List;

/**
 * Smart search service interface that provides intelligent image search capabilities
 * Supports multiple search strategies including hybrid search, vector-only search,
 * text-only search, and image-to-image search;
 *
 * @author Ryan
 * @since 2025/12/15
 */
public interface SmartSearchService {

    /**
     * Perform intelligent search based on the provided search parameters
     * The search strategy is determined by the strategy type in the query DTO
     *
     * @param query search parameters containing keyword, limits, minimum score threshold,
     *              and search strategy configuration
     * @return list of search results with image metadata, scores, and highlighted content
     * @see SearchQueryDTO#getKeyword() search keyword or text query
     * @see SearchQueryDTO#getLimit() maximum number of results to return
     * @see SearchQueryDTO#getMinScore() minimum similarity score threshold (0.0-1.0)
     * @see SearchQueryDTO#isEnableOcr() whether to enable OCR-based text search
     */
    List<SearchResultDTO> search(SearchQueryDTO query);

    /**
     * Perform vector-based search using the provided document ID
     *
     * @param docId unique identifier of the document to search by vector
     * @return list of search results with image metadata, scores, and highlighted content
     */
    List<SearchResultDTO> searchByVector(String docId);
}