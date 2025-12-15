package com.smart.vision.core.service.search;

import com.smart.vision.core.manager.BailianEmbeddingManager;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.strategy.RetrievalStrategy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.smart.vision.core.service.convert.ImageDocConvertor.convertToSearchResultDTO;

@Service
public class SmartSearchService {

    @Resource
    private BailianEmbeddingManager embeddingManager;
    @Resource
    private Map<String, RetrievalStrategy> strategyMap;

    public List<SearchResultDTO> search(SearchQueryDTO query) {
        query = validQuery(query);
        List<Float> queryVector = embeddingManager.embedText(query.getText());
        RetrievalStrategy strategy = strategyMap.get("HYBRID");
        List<ImageDocument> docs = strategy.search(query, queryVector);
        return convertToSearchResultDTO(docs);
    }
}