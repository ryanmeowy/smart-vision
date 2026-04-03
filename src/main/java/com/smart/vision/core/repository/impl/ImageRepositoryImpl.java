package com.smart.vision.core.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.smart.vision.core.convertor.SearchResultConvertor;
import com.smart.vision.core.exception.ApiError;
import com.smart.vision.core.exception.InfraException;
import com.smart.vision.core.model.dto.HybridSearchParamDTO;
import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepositoryCustom;
import com.smart.vision.core.query.factory.SearchRequestFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

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
            throw new InfraException(ApiError.SEARCH_BACKEND_UNAVAILABLE);
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
            throw new InfraException(ApiError.SEARCH_BACKEND_UNAVAILABLE);
        }
    }

    @Override
    public List<ImageSearchResultDTO> vectorSearch(List<Float> vector, Integer topK) {
        try {
            SearchRequest request = searchRequestFactory.buildVectorOnly(vector, topK);
            SearchResponse<ImageDocument> response = esClient.search(request, ImageDocument.class);
            return converter.convert2Doc(response);
        } catch (Exception e) {
            log.error("Execution of vector-only search failed", e);
            throw new InfraException(ApiError.SEARCH_BACKEND_UNAVAILABLE);
        }
    }

    @Override
    public List<ImageSearchResultDTO> textSearch(String keyword, Integer limit, Boolean enableOcr) {
        try {
            SearchRequest request = searchRequestFactory.buildTextOnly(keyword, limit, enableOcr);
            SearchResponse<ImageDocument> response = esClient.search(request, ImageDocument.class);
            return converter.convert2Doc(response);
        } catch (Exception e) {
            log.error("Execution of text-only search failed", e);
            throw new InfraException(ApiError.SEARCH_BACKEND_UNAVAILABLE);
        }
    }

    @Override
    public ImageDocument findDuplicate(List<Float> vector, double threshold) {
        try {
            SearchRequest request = searchRequestFactory.buildDuplicate(vector, threshold);
            SearchResponse<ImageDocument> response = esClient.search(request, ImageDocument.class);
            if (response.hits() == null || response.hits().hits() == null || response.hits().hits().isEmpty()) {
                log.warn("Duplicate check returned empty hits");
                return null;
            }
            return response.hits().hits().getFirst().source();
        } catch (Exception e) {
            log.error("Duplicate check failed", e);
            throw new InfraException(ApiError.SEARCH_BACKEND_UNAVAILABLE);
        }
    }
}
