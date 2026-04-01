package com.smart.vision.core.strategy.impl;

import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.enums.StrategyTypeEnum;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.strategy.RetrievalStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.smart.vision.core.constant.SearchConstant.DEFAULT_RESULT_LIMIT;

/**
 * Text-only retrieval strategy (BM25 style keyword search).
 */
@Component
@RequiredArgsConstructor
public class TextOnlyRetrievalStrategy implements RetrievalStrategy {

    private final ImageRepository imageRepository;

    @Override
    public List<ImageSearchResultDTO> search(SearchQueryDTO query, List<Float> queryVector) {
        if (query == null) {
            return List.of();
        }
        Integer limit = query.getLimit() == null ? DEFAULT_RESULT_LIMIT : query.getLimit();
        Boolean enableOcr = query.getEnableOcr() == null ? Boolean.TRUE : query.getEnableOcr();
        return imageRepository.textSearch(query.getKeyword(), limit, enableOcr);
    }

    @Override
    public StrategyTypeEnum getType() {
        return StrategyTypeEnum.TEXT_ONLY;
    }
}

