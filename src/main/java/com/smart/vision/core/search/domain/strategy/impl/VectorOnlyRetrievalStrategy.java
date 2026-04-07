package com.smart.vision.core.search.domain.strategy.impl;

import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.enums.StrategyTypeEnum;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.search.domain.strategy.RetrievalStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_TOP_K;

/**
 * Pure vector retrieval strategy (KNN only).
 */
@Component
@RequiredArgsConstructor
public class VectorOnlyRetrievalStrategy implements RetrievalStrategy {

    private final ImageRepository imageRepository;

    @Override
    public List<ImageSearchResultDTO> search(SearchQueryDTO query, List<Float> queryVector) {
        Integer topK = query == null || query.getTopK() == null ? DEFAULT_TOP_K : query.getTopK();
        return imageRepository.vectorSearch(queryVector, topK);
    }

    @Override
    public StrategyTypeEnum getType() {
        return StrategyTypeEnum.VECTOR_ONLY;
    }
}

