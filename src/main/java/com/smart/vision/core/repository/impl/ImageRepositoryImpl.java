package com.smart.vision.core.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_EMBEDDING_BOOST;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_FIELD_NAME_BOOST;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_NUM_CANDIDATES;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_OCR_BOOST;
import static com.smart.vision.core.constant.CommonConstant.EMBEDDING_FIELD;
import static com.smart.vision.core.constant.CommonConstant.FILE_NAME_FIELD;
import static com.smart.vision.core.constant.CommonConstant.IMAGE_INDEX;
import static com.smart.vision.core.constant.CommonConstant.MINIMUM_SIMILARITY;
import static com.smart.vision.core.constant.CommonConstant.NUM_CANDIDATES_FACTOR;
import static com.smart.vision.core.constant.CommonConstant.OCR_FIELD;

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

    @Override
    public List<ImageSearchResultDTO> hybridSearch(SearchQueryDTO query, List<Float> queryVector) {
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
                .index(IMAGE_INDEX)
                .size(query.getLimit());

//        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
//            requestBuilder.query(q -> q
//                    .bool(b -> b
//                            .should(s -> s.match(m -> m.field(OCR_FIELD).query(query.getKeyword()).boost(DEFAULT_OCR_BOOST)))
////                            .should(s -> s.match(m -> m.field(FILE_NAME_FIELD).query(query.getKeyword()).boost(DEFAULT_FIELD_NAME_BOOST)))
//                    )
//            );
//        }

        // 3. 构建 KNN (处理 vector 可能为空的情况)
        if (queryVector != null && !queryVector.isEmpty()) {
            requestBuilder.knn(k -> k
                            .field(EMBEDDING_FIELD)
                            .queryVector(queryVector)
                            .k(query.getTopK())
//                            .numCandidates(Math.max(100, query.getTopK() * 2))
                            .boost(DEFAULT_EMBEDDING_BOOST)
                            .similarity(null == query.getSimilarity() ? MINIMUM_SIMILARITY : query.getSimilarity())

            );
        }

        // 4. 处理排序
        requestBuilder.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
        requestBuilder.sort(so -> so.field(f -> f.field("id.keyword").order(SortOrder.Asc)));

        // 5. 关键：只有当 searchAfter 不为空时才设置
        if (query.getSearchAfter() != null && !query.getSearchAfter().isEmpty()) {
             requestBuilder.searchAfter(query.getSearchAfter());
        }
        SearchRequest request = requestBuilder.build();
        try {
//            log.info("Hybrid search request: {}", request);
            SearchResponse<ImageDocument> response = esClient.search(request, ImageDocument.class);
            return convert2Doc(response);
        } catch (Exception e) {
            log.error("Hybrid search execution failed", e);
            return Collections.emptyList();
        }
    }

    private List<ImageSearchResultDTO> convert2Doc(SearchResponse<ImageDocument> response) {
        List<Hit<ImageDocument>> hits = Optional.ofNullable(response)
                .map(SearchResponse::hits)
                .map(HitsMetadata::hits)
                .orElse(Collections.emptyList());

        return hits.stream()
                .filter(hit -> hit.source() != null)
                .map(hit -> {
                    ImageDocument doc = hit.source();
                    doc.setId(hit.id());
                    return ImageSearchResultDTO.builder()
                            .document(doc)
                            .score(hit.score())
                            .sortValues(hit.sort())
                            .build();
                }).collect(Collectors.toList());

    }

    @Override
    public List<ImageSearchResultDTO> searchSimilar(List<Float> vector, Integer topK, String excludeDocId) {
        if (CollectionUtils.isEmpty(vector)) {
            return Collections.emptyList();
        }
        try {
            SearchResponse<ImageDocument> response = esClient.search(s -> s
                            .index(IMAGE_INDEX)
                            .query(q -> q
                                    .bool(b -> b.mustNot(mn -> mn.ids(i -> i.values(excludeDocId)))))
                            .knn(builder -> builder
                                    .field(EMBEDDING_FIELD)
                                    .queryVector(vector)
                                    .k(topK)
                                    .numCandidates(Math.min(NUM_CANDIDATES_FACTOR * topK, DEFAULT_NUM_CANDIDATES))
                                    .boost(DEFAULT_EMBEDDING_BOOST)
                                    .similarity(MINIMUM_SIMILARITY))
                            .size(topK),
                    ImageDocument.class
            );
            return convert2Doc(response);
        } catch (IOException e) {
            log.error("Execution of finding similar failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public ImageDocument findDuplicate(List<Float> vector, double threshold) {
        try {
            SearchResponse<ImageDocument> response = esClient.search(s -> s
                            .index(IMAGE_INDEX)
                            .knn(k -> k
                                    .field(EMBEDDING_FIELD)
                                    .queryVector(vector)
                                    .k(1) // top 1
                                    .numCandidates(10)
                                    .similarity((float) threshold)
                            )
                            .size(1),
                    ImageDocument.class
            );

            if (!response.hits().hits().isEmpty()) {
                return response.hits().hits().get(0).source();
            }
            return null;
        } catch (IOException e) {
            log.error("Duplicate check failed", e);
            return null;
        }
    }
}