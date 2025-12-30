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
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_TAG_BOOST;
import static com.smart.vision.core.constant.CommonConstant.IMAGE_INDEX;
import static com.smart.vision.core.constant.CommonConstant.MINIMUM_SIMILARITY;
import static com.smart.vision.core.constant.CommonConstant.NUM_CANDIDATES_FACTOR;
import static com.smart.vision.core.constant.CommonConstant.SIMILAR_QUERIES_SIMILARITY;
import static com.smart.vision.core.util.ScoreUtil.mapScoreToPercentage;

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

        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            requestBuilder.query(q -> q
                    .bool(b -> b
                            .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("ocrContent").query(query.getKeyword()))).boost(DEFAULT_OCR_BOOST)))
                            .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("tags").query(query.getKeyword()))).boost(DEFAULT_TAG_BOOST)))
                            .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("fileName").query(query.getKeyword()))).boost(DEFAULT_FIELD_NAME_BOOST)))
                    )
            );
        }

        if (!CollectionUtils.isEmpty(queryVector)) {
            requestBuilder.knn(k -> k
                    .field("imageEmbedding")
                    .queryVector(queryVector)
                    .k(query.getTopK())
                    .numCandidates(Math.max(100, query.getTopK() * 2))
                    .boost(DEFAULT_EMBEDDING_BOOST)
                    .similarity(null == query.getSimilarity() ? MINIMUM_SIMILARITY : query.getSimilarity())
            );
        }

        requestBuilder.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
        requestBuilder.sort(so -> so.field(f -> f.field("id").order(SortOrder.Asc)));

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
                .filter(hit -> hit.id() != null)
                .filter(hit -> hit.score() != null)
                .map(hit -> {
                    ImageDocument doc = hit.source();
                    doc.setId(Long.parseLong(hit.id()));
                    return ImageSearchResultDTO.builder()
                            .document(doc)
                            .score(mapScoreToPercentage(hit.score()))
                            .sortValues(hit.sort())
                            .build();
                }).collect(Collectors.toList());
    }

    @Override
    public List<ImageSearchResultDTO> searchSimilar(List<Float> vector, Integer topK, String excludeDocId) {
        if (CollectionUtils.isEmpty(vector)) {
            return Collections.emptyList();
        }
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
                .index(IMAGE_INDEX)
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