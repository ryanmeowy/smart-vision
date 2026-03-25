package com.smart.vision.core.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.smart.vision.core.convertor.SearchResultConvertor;
import com.smart.vision.core.model.dto.HybridSearchParamDTO;
import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepositoryCustom;
import com.smart.vision.core.query.factory.SearchRequestFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * Hybrid search implementation
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageRepositoryImpl implements ImageRepositoryCustom {

    private final ElasticsearchClient esClient;
    private final SearchResultConvertor converter;
    private final SearchRequestFactory searchRequestFactory;

    @Override
    public List<ImageSearchResultDTO> hybridSearch(HybridSearchParamDTO paramDTO) {
        try {
            SearchRequest request = searchRequestFactory.buildHybrid(paramDTO);
            SearchResponse<ImageDocument> response = esClient.search(request, ImageDocument.class);
            return converter.convert2Doc(response);
        } catch (Exception e) {
            log.error("Hybrid search execution failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<ImageSearchResultDTO> searchSimilar(List<Float> vector, Integer topK, String excludeDocId) {
        try {
            SearchRequest request = searchRequestFactory.buildSimilar(vector, topK, excludeDocId);
            SearchResponse<ImageDocument> response = esClient.search(request, ImageDocument.class);
            return converter.convert2Doc(response);
        } catch (Exception e) {
            log.error("Execution of finding similar failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public ImageDocument findDuplicate(List<Float> vector, double threshold) {
        try {
            SearchRequest request = searchRequestFactory.buildDuplicate(vector, threshold);
            SearchResponse<ImageDocument> response = esClient.search(request, ImageDocument.class);
            return response.hits().hits().getFirst().source();
        } catch (Exception e) {
            log.error("Duplicate check failed", e);
            return null;
        }
    }
}