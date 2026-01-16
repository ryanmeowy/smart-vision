package com.smart.vision.core.component;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ES Generic Batch Operations Tool
 * Solves the problem of automatic Index and ID resolution for generic entities
 *
 * @author Ryan
 * @since 2025/12/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsBatchTemplate {

    private final ElasticsearchClient esClient;
    // Core converter of Spring Data ES, read @Document and @Id
    private final ElasticsearchConverter elasticsearchConverter;

    /**
     * Generic batch write (supports partial failure handling)
     *
     * @param items Entity list
     * @param <T>   Entity type
     * @return Actual number of successfully written items
     */
    public <T> int bulkSave(List<T> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        Class<?> clazz = items.getFirst().getClass();
        IndexCoordinates indexCoordinates = elasticsearchConverter.getMappingContext()
                .getRequiredPersistentEntity(clazz)
                .getIndexCoordinates();
        String indexName = indexCoordinates.getIndexName();

        try {
            BulkRequest.Builder br = new BulkRequest.Builder();

            for (T item : items) {
                Object identifier = elasticsearchConverter.getMappingContext()
                        .getRequiredPersistentEntity(clazz)
                        .getIdentifierAccessor(item)
                        .getIdentifier();
                String id = Optional.ofNullable(identifier).map(String::valueOf).orElse(null);
                if (id == null) {
                    log.warn("ES Bulk write failed - ID is null for item: {}", item);
                    continue;
                }
                br.operations(op -> op
                    .index(idx -> idx
                        .index(indexName)
                        .id(id)
                        .document(item)
                    )
                );
            }

            BulkResponse response = esClient.bulk(br.build());

            if (response.errors()) {
                List<String> errorIds = new ArrayList<>();
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.error("Bulk write failed - Index: {}, ID: {}, Reason: {}",
                                item.index(), item.id(), item.error().reason());
                        errorIds.add(item.id());
                    }
                }
                // Return: total count - failed count
                return items.size() - errorIds.size();
            }

            return items.size();

        } catch (Exception e) {
            log.error("ES Bulk IO Exception", e);
            throw new RuntimeException("ES batch write system exception");
        }
    }
}