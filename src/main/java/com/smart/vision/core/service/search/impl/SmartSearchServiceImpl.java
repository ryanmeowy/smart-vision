package com.smart.vision.core.service.search.impl;

import com.smart.vision.core.manager.BailianEmbeddingManager;
import com.smart.vision.core.manager.HotSearchManager;
import com.smart.vision.core.model.dto.ImageSearchResult;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.service.convert.ImageDocConvertor;
import com.smart.vision.core.service.search.SmartSearchService;
import com.smart.vision.core.strategy.RetrievalStrategy;
import com.smart.vision.core.strategy.StrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

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
    private final ImageRepository imageRepository;
    private final ImageDocConvertor imageDocConvertor;
    private final HotSearchManager hotSearchManager;
    private final StrategyFactory strategyFactory;

    public List<SearchResultDTO> search(SearchQueryDTO query) {
        if (StringUtils.hasText(query.getKeyword())) {
            hotSearchManager.incrementScore(query.getKeyword());
        }
        List<Float> queryVector = embeddingManager.embedText(query.getKeyword());
        RetrievalStrategy strategy = strategyFactory.getStrategy(query.getSearchType());
        List<ImageSearchResult> docs = strategy.search(query, queryVector);
        return imageDocConvertor.convert2SearchResultDTO(docs);
    }

    public List<SearchResultDTO> searchByVector(String docId) {
        ImageDocument sourceDoc = imageRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Image does not exist or has been deleted"));

        List<Float> embedding = sourceDoc.getImageEmbedding();

        if (CollectionUtils.isEmpty(embedding)) {
            throw new RuntimeException("The image has not been vectorized yet");
        }
        // Perform search (find the 10 most similar images)
        List<ImageSearchResult> similarDocs = imageRepository.searchSimilar(embedding, 10, docId);
        return imageDocConvertor.convert2SearchResultDTO(similarDocs);
    }
}