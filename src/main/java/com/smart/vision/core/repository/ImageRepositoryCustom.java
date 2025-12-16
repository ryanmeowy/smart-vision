
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
     * High-performance batch write
     * @param documents Document list
     * @return Number of successfully written documents
     */
    int bulkSave(List<ImageDocument> documents);

}