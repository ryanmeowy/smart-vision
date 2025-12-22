package com.smart.vision.core.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
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

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_NUM_CANDIDATES;
import static com.smart.vision.core.constant.CommonConstant.HYBRID_SEARCH_DEFAULT_MIN_SCORE;
import static com.smart.vision.core.constant.CommonConstant.IMAGE_INDEX;

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
    public List<ImageDocument> hybridSearch(SearchQueryDTO query, List<Float> queryVector) {
        try {
            SearchResponse<ImageDocument> response = esClient.search(s -> s
                            .index(IMAGE_INDEX)
                            .query(q -> q
                                    .bool(b -> b
                                            // 1. OCR text matching (BM25)
                                            .should(sh -> sh
                                                    .match(m -> m
                                                            .field("ocrContent")
                                                            .query(query.getKeyword())
                                                            .boost(0.5f)
                                                    )
                                            )
                                            // 2. Filename/URL exact/tokenized matching
                                            .should(sh -> sh
                                                    .match(m -> m
                                                            .field("filename")
                                                            .query(query.getKeyword())
                                                            .boost(0.2f)
                                                    )
                                            )
                                    )
                            )
                            // 3. Vector KNN search (HNSW index)
                            .knn(k -> k
                                    .field("imageEmbedding")
                                    .queryVector(queryVector)
                                    .k(query.getLimit())
                                    .numCandidates(DEFAULT_NUM_CANDIDATES)
                                    .boost(0.9f)
                                    // Minimum similarity threshold, only applies to KNN search, hybrid search score normalization is complex
                                    .similarity(null == query.getMinScore() ? HYBRID_SEARCH_DEFAULT_MIN_SCORE : query.getMinScore())
                            )
                            .size(query.getLimit()),
                    ImageDocument.class
            );
            return convert2Doc(response);
        } catch (IOException e) {
            log.error("Hybrid search execution failed", e);
            return Collections.emptyList();
        }
    }

    private List<ImageDocument> convert2Doc(SearchResponse<ImageDocument> response) {
        List<Hit<ImageDocument>> hits = Optional.ofNullable(response)
                .map(SearchResponse::hits)
                .map(HitsMetadata::hits)
                .orElse(Collections.emptyList());

        return hits.stream().map(hit -> {
            ImageDocument doc = hit.source();
            if (doc != null) {
                doc.setScore(hit.score());
                doc.setId(hit.id());
            }
            return doc;
        }).collect(Collectors.toList());

    }

    @Override
    public List<ImageDocument> searchSimilar(List<Float> vector, int limit, String excludeDocId) {
        if (CollectionUtils.isEmpty(vector)) {
            return Collections.emptyList();
        }

        try {
            SearchResponse<ImageDocument> response = esClient.search(s -> s
                            .index(IMAGE_INDEX)
                            .query(q -> q
                                    .bool(b -> b
                                            .mustNot(mn -> mn
                                                    .ids(i -> i.values(excludeDocId))
                                            )
                                    )
                            )
                            .knn(k -> k
                                    .field("imageEmbedding")
                                    .queryVector(vector)
                                    .k(limit)
                                    .numCandidates(100)
                            )
                            .size(limit),
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
                                    .field("imageEmbedding")
                                    .queryVector(vector)
                                    .k(1) // top 1
                                    .numCandidates(10)
                                    .similarity((float) threshold)// [Core] ES 8.x supports direct filtering of low-score results
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