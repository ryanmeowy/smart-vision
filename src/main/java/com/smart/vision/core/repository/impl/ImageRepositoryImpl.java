package com.smart.vision.core.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
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
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_NUM_CANDIDATES;
import static com.smart.vision.core.constant.CommonConstant.NUM_CANDIDATES_FACTOR;
import static com.smart.vision.core.constant.CommonConstant.SIMILAR_QUERIES_SIMILARITY;
import static com.smart.vision.core.constant.CommonConstant.SMART_GALLERY_V1;

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
        SortContext scoreSort = SortContext.builder().kind(SortOptions.Kind.Score).order(SortOrder.Desc).build();
        SortContext idSort = SortContext.builder().kind(SortOptions.Kind.Field).field("id").order(SortOrder.Asc).build();
        QueryContext context = contextConverter.context4HybridSearch(query, queryVector, scoreSort, idSort);
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
        if (CollectionUtils.isEmpty(vector)) {
            return Collections.emptyList();
        }
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
                .index(SMART_GALLERY_V1)
                .size(topK);
        requestBuilder.query(q -> q.bool(b -> b.mustNot(mn -> mn.term(t -> t.field("id").value(Long.parseLong(excludeDocId))))));
        requestBuilder.knn(builder -> builder
                .field("imageEmbedding")
                .queryVector(vector)
                .filter(f -> f.bool(b -> b.mustNot(mn -> mn.term(t -> t.field("id").value(Long.parseLong(excludeDocId))))))
                .k(topK)
                .numCandidates(Math.min(NUM_CANDIDATES_FACTOR * topK, DEFAULT_NUM_CANDIDATES))
                .similarity(SIMILAR_QUERIES_SIMILARITY));
        requestBuilder.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
        requestBuilder.sort(so -> so.field(f -> f.field("id").order(SortOrder.Asc)));
        SearchRequest request = requestBuilder.build();
        try {
            SearchResponse<ImageDocument> response = esClient.search(request, ImageDocument.class);
            return converter.convert2Doc(response);
        } catch (IOException e) {
            log.error("Execution of finding similar failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public ImageDocument findDuplicate(List<Float> vector, double threshold) {
        try {
            SearchResponse<ImageDocument> response = esClient.search(s -> s
                            .index(SMART_GALLERY_V1)
                            .knn(k -> k
                                    .field("imageEmbedding")
                                    .queryVector(vector)
                                    .k(1) // top 1
                                    .numCandidates(10)
                                    .similarity((float) threshold)
                            )
                            .size(1),
                    ImageDocument.class
            );

            if (!response.hits().hits().isEmpty()) {
                return response.hits().hits().getFirst().source();
            }
            return null;
        } catch (IOException e) {
            log.error("Duplicate check failed", e);
            return null;
        }
    }
}