
package com.smart.vision.core.repository;

import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;

import java.util.List;

/**
 * Custom extension interface for defining complex hybrid search methods;
 *
 * @author Ryan
 * @since 2025/12/15
 */
public interface ImageRepositoryCustom {
    /**
     * Hybrid search: vector + OCR text
     *
     * @param query       user request
     * @param queryVector text converted to vector
     * @return list of matching documents
     */
    List<ImageDocument> hybridSearch(SearchQueryDTO query, List<Float> queryVector);

    /**
     * Search for similar documents based on vector (supports excluding specific ID)
     * @param vector Array of vectors
     * @param limit Number of results
     * @param excludeDocId Document ID to exclude (usually exclude itself when searching for similar)
     * @return List of documents
     */
    List<ImageDocument> searchSimilar(List<Float> vector, int limit, String excludeDocId);

}