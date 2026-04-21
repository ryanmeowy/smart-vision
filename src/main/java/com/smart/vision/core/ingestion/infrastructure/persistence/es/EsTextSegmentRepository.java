package com.smart.vision.core.ingestion.infrastructure.persistence.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.smart.vision.core.common.config.KbSegmentConfig;
import com.smart.vision.core.common.exception.ApiError;
import com.smart.vision.core.common.exception.InfraException;
import com.smart.vision.core.ingestion.domain.model.TextChunk;
import com.smart.vision.core.ingestion.domain.port.TextSegmentRepository;
import com.smart.vision.core.search.domain.model.KbAssetTypeEnum;
import com.smart.vision.core.search.domain.model.KbSegmentTypeEnum;
import com.smart.vision.core.search.infrastructure.persistence.es.document.KbSegmentDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Elasticsearch persistence for text chunks in kb_segment index.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EsTextSegmentRepository implements TextSegmentRepository {

    private final ElasticsearchClient esClient;
    private final KbSegmentConfig kbSegmentConfig;

    @Override
    public void save(String assetId, List<TextChunk> chunks) {
        if (!StringUtils.hasText(assetId) || chunks == null || chunks.isEmpty()) {
            return;
        }
        String indexName = kbSegmentConfig.getWriteTargetName();
        long now = System.currentTimeMillis();
        try {
            var requestBuilder = new co.elastic.clients.elasticsearch.core.BulkRequest.Builder();
            int operationCount = 0;
            for (TextChunk chunk : chunks) {
                if (chunk == null || !StringUtils.hasText(chunk.getSegmentId())) {
                    continue;
                }
                KbSegmentDocument document = toDocument(chunk, now);
                requestBuilder.operations(op -> op.index(i -> i
                        .index(indexName)
                        .id(chunk.getSegmentId())
                        .document(document)
                ));
                operationCount++;
            }
            if (operationCount == 0) {
                return;
            }
            BulkResponse response = esClient.bulk(requestBuilder.build());
            if (response.errors()) {
                String reason = response.items().stream()
                        .map(BulkResponseItem::error)
                        .filter(java.util.Objects::nonNull)
                        .map(error -> error.reason())
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse("kb_segment bulk save failed");
                throw new InfraException(ApiError.SEARCH_BACKEND_UNAVAILABLE, reason);
            }
        } catch (InfraException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to persist text segments to index [{}], assetId={}", indexName, assetId, e);
            throw new InfraException(ApiError.SEARCH_BACKEND_UNAVAILABLE, "Failed to persist text segments", e);
        }
    }

    private KbSegmentDocument toDocument(TextChunk chunk, long now) {
        KbSegmentDocument document = new KbSegmentDocument();
        document.setSegmentId(chunk.getSegmentId());
        document.setAssetId(chunk.getAssetId());
        document.setAssetType(KbAssetTypeEnum.TEXT.name());
        document.setSegmentType(KbSegmentTypeEnum.TEXT_CHUNK.name());
        document.setTitle(chunk.getTitle());
        document.setContentText(chunk.getChunkText());
        document.setPageNo(chunk.getPageNo());
        document.setChunkOrder(chunk.getChunkOrder());
        document.setSourceRef(chunk.getSourceRef());
        document.setCreatedAt(now);
        return document;
    }
}
