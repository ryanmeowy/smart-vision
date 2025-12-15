package com.smart.vision.core.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ImageRepositoryImpl implements ImageRepositoryCustom {

    @Resource
    private ElasticsearchClient esClient;

    @Override
    public List<ImageDocument> hybridSearch(SearchQueryDTO query, List<Float> queryVector) {
        try {
            SearchResponse<ImageDocument> response = esClient.search(s -> s
                            .index("smart_gallery_v1")
                            .query(q -> q
                                    .bool(b -> b
                                            // 1. OCR 文本匹配 (BM25)
                                            .should(sh -> sh
                                                    .match(m -> m
                                                            .field("ocrContent")
                                                            .query(query.getText())
                                                            .boost(0.5f)
                                                    )
                                            )
                                            // 2. 文件名/URL 精确/分词匹配
                                            .should(sh -> sh
                                                    .match(m -> m
                                                            .field("filename")
                                                            .query(query.getText())
                                                            .boost(0.2f)
                                                    )
                                            )
                                    )
                            )
                            // 3. 向量 KNN 检索 (HNSW 索引)
                            .knn(k -> k
                                    .field("imageEmbedding")
                                    .queryVector(queryVector)
                                    .k(query.getLimit())
                                    .numCandidates(100)
                                    .boost(0.9f)
                                    // 最小相似度阈值, 仅作用于knn检索, 混合检索分数归一化比较复杂
                                    .similarity(query.getMinScore())
                            )
                            .size(query.getLimit()),
                    ImageDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("混合检索执行失败", e);
            return Collections.emptyList();
        }
    }
}