package com.smart.vision.core.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.google.common.collect.Lists;
import com.smart.vision.core.converter.QueryContextConverter;
import com.smart.vision.core.converter.SearchResultConverter;
import com.smart.vision.core.model.context.QueryContext;
import com.smart.vision.core.model.context.SortContext;
import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.processor.QueryProcessor;
import com.smart.vision.core.repository.ImageRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
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
    private final List<QueryProcessor> queryProcessorList;
    private final SearchResultConverter converter;
    private final QueryContextConverter contextConverter;

    @Override
    public List<ImageSearchResultDTO> hybridSearch(SearchQueryDTO query, List<Float> queryVector) {
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder();
        QueryContext context = contextConverter.context4HybridSearch(query, queryVector, defaultSort());
        queryProcessorList.forEach(x -> x.process(context, requestBuilder));
        try {
            SearchResponse<ImageDocument> response = esClient.search(requestBuilder.build(), ImageDocument.class);
            return converter.convert2Doc(response);
        } catch (Exception e) {
            log.error("Hybrid search execution failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<ImageSearchResultDTO> searchSimilar(List<Float> vector, Integer topK, String excludeDocId) {
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder();
        QueryContext context = contextConverter.context4SimilarSearch(vector, topK, excludeDocId, defaultSort());
        queryProcessorList.forEach(x -> x.process(context, requestBuilder));
        try {
            SearchResponse<ImageDocument> response = esClient.search(requestBuilder.build(), ImageDocument.class);
            return converter.convert2Doc(response);
        } catch (IOException e) {
            log.error("Execution of finding similar failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public ImageDocument findDuplicate(List<Float> vector, double threshold) {
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder();
        QueryContext context = contextConverter.context4DuplicateSearch(vector, threshold);
        queryProcessorList.forEach(x -> x.process(context, requestBuilder));
        try {
            SearchResponse<ImageDocument> response = esClient.search(requestBuilder.build(), ImageDocument.class);
            return response.hits().hits().getFirst().source();
        } catch (Exception e) {
            log.error("Duplicate check failed", e);
            return null;
        }
    }

    private List<SortContext> defaultSort() {
        SortContext scoreSort = SortContext.builder().kind(SortOptions.Kind.Score).order(SortOrder.Desc).build();
        SortContext idSort = SortContext.builder().kind(SortOptions.Kind.Field).field("id").order(SortOrder.Asc).build();
        return Lists.newArrayList(scoreSort, idSort);
    }
}