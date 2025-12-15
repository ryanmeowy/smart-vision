package com.smart.vision.core.repository;

import com.smart.vision.core.model.entity.ImageDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    /**
     * Simple query by URL (used for duplicate checking)
     */
    List<ImageDocument> findByUrl(String url);

    /**
     * Simple full-text search based on OCR content only (without vector)
     * For complex hybrid search, it is recommended to use ElasticsearchClient to build in Service/Strategy layer
     */
    List<ImageDocument> findByOcrContentMatches(String text);
}