
package com.smart.vision.core.strategy;

import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.model.enums.StrategyType;
import com.smart.vision.core.repository.ImageRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hybrid retrieval strategy implementation that combines multiple search approaches
 * This strategy integrates vector similarity search with text-based search (OCR content and filename)
 * to provide comprehensive and accurate image search results
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Component
public class HybridRetrievalStrategy implements RetrievalStrategy {

    @Resource
    private ImageRepository imageRepository;

    /**
     * Execute hybrid search by combining vector embedding search with text-based search
     * This method leverages both semantic similarity from vector embeddings and
     * exact/text matching from OCR content and filenames
     *
     * @param query the search query parameters containing keyword, limits, and scoring thresholds
     * @param queryVector the vector representation of the search keyword for semantic similarity
     * @return list of image documents ranked by combined relevance scores
     */
    @Override
    public List<ImageDocument> search(SearchQueryDTO query, List<Float> queryVector) {
        return imageRepository.hybridSearch(query, queryVector);
    }

    /**
     * Get the strategy type identifier for this implementation
     *
     * @return StrategyType.HYBRID indicating this is a hybrid search strategy
     * @see StrategyType#HYBRID
     */
    @Override
    public StrategyType getType() {
        return StrategyType.HYBRID;
    }
}