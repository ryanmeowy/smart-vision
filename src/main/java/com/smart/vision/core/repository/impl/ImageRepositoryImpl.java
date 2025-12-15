package com.smart.vision.core.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepositoryCustom;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.CommonConstant.*;

/**
 * Hybrid search implementation
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Repository
public class ImageRepositoryImpl implements ImageRepositoryCustom {

    @Resource
    private ElasticsearchClient esClient;

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

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("Hybrid search execution failed", e);
            return Collections.emptyList();
        }
    }
}