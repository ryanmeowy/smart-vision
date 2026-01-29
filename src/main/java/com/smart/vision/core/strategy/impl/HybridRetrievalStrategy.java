
package com.smart.vision.core.strategy.impl;

import com.smart.vision.core.ai.ContentGenerationService;
import com.smart.vision.core.config.SimilarityConfig;
import com.smart.vision.core.model.dto.HybridSearchParamDTO;
import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.enums.StrategyTypeEnum;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.strategy.RetrievalStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_TOP_K;
import static com.smart.vision.core.constant.SearchConstant.DEFAULT_RESULT_LIMIT;

/**
 * Hybrid retrieval strategy implementation that combines multiple search approaches
 * This strategy integrates vector similarity search with text-based search (OCR content and filename)
 * to provide comprehensive and accurate image search results
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Component
@RequiredArgsConstructor
public class HybridRetrievalStrategy implements RetrievalStrategy {

    private final ImageRepository imageRepository;
    private final SimilarityConfig similarityConfig;
    private final ContentGenerationService generationService;

    /**
     * Execute hybrid search by combining vector embedding search with text-based search
     * This method leverages both semantic similarity from vector embeddings and
     * exact/text matching from OCR content and filenames
     *
     * @param query       the search query parameters containing keyword, limits, and scoring thresholds
     * @param queryVector the vector representation of the search keyword for semantic similarity
     * @return list of image documents ranked by combined relevance scores
     */
    @Override
    public List<ImageSearchResultDTO> search(SearchQueryDTO query, List<Float> queryVector) {
        HybridSearchParamDTO paramDTO = HybridSearchParamDTO.builder()
                .queryVector(queryVector)
                .topK(null == query.getTopK() ? DEFAULT_TOP_K : query.getTopK())
                .similarity(null == query.getSimilarity() ? similarityConfig.forHybridSearch() : query.getSimilarity())
                .limit(null == query.getLimit() ? DEFAULT_RESULT_LIMIT : query.getLimit())
                .keyword(query.getKeyword())
                .graphTriples(generationService.praseTriplesFromKeyword(query.getKeyword()))
                .build();
        return imageRepository.hybridSearch(paramDTO);
    }

    /**
     * Get the strategy type identifier for this implementation
     *
     * @return StrategyType.HYBRID indicating this is a hybrid search strategy
     * @see StrategyTypeEnum#HYBRID
     */
    @Override
    public StrategyTypeEnum getType() {
        return StrategyTypeEnum.HYBRID;
    }
}