package com.smart.vision.core.service.search.impl;

import com.smart.vision.core.manager.BailianEmbeddingManager;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.service.search.SmartSearchService;
import com.smart.vision.core.strategy.RetrievalStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.smart.vision.core.service.convert.ImageDocConvertor.convertToSearchResultDTO;

/**
 * Smart search service implementation
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Service
@RequiredArgsConstructor
public class SmartSearchServiceImpl implements SmartSearchService {
    private final BailianEmbeddingManager embeddingManager;
    private final Map<String, RetrievalStrategy> strategyMap;

    public List<SearchResultDTO> search(SearchQueryDTO query) {
//        query = validQuery(query);
        List<Float> queryVector = embeddingManager.embedText(query.getKeyword());
        RetrievalStrategy strategy = strategyMap.get("HYBRID");
        List<ImageDocument> docs = strategy.search(query, queryVector);
        return convertToSearchResultDTO(docs);
    }
}
