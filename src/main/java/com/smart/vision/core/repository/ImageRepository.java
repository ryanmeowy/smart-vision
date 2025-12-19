package com.smart.vision.core.repository;

import com.smart.vision.core.model.entity.ImageDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Elasticsearch data access layer;
 * Generic 1: Entity class;
 * Generic 2: Primary key type;
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Repository
public interface ImageRepository extends ElasticsearchRepository<ImageDocument, String>, ImageRepositoryCustom {
    // Basic CRUD operations are provided by the parent interface, no need to write manually
    // save(doc)
    // findById(id)
}