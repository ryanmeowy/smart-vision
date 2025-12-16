package com.smart.vision.core.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.smart.vision.core.component.EsBatchTemplate;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
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

    private final EsBatchTemplate esBatchTemplate;

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

    @Override
    public int bulkSave(List<ImageDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        try {
            // 1. Build Bulk request
            BulkRequest.Builder br = new BulkRequest.Builder();

            for (ImageDocument doc : documents) {
                br.operations(op -> op
                        .index(idx -> idx
                                .index("smart_gallery_v1") // Index name
                                .id(doc.getId())           // Explicitly specify ID (if exists)
                                .document(doc)             // Put document object
                        )
                );
            }

            // 2. Execute request
            BulkResponse response = esClient.bulk(br.build());

            // 3. [Critical] Handle partial failure
            if (response.errors()) {
                // Senior developers will print specific error logs here instead of simply throwing exceptions
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .forEach(item -> log.error("Document write failed ID [{}]: {}", item.id(), item.error().reason()));

                // Return actual successful count
                return (int) response.items().stream().filter(i -> i.error() == null).count();
            }

            return documents.size();

        } catch (IOException e) {
            log.error("Bulk write IO exception occurred", e);
            throw new RuntimeException("ES batch write failed");
        }
    }
}