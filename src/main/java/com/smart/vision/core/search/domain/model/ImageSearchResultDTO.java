package com.smart.vision.core.search.domain.model;

import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Image search result model
 *
 * @author Ryan
 * @since 2025/12/23
 */
@Data
@Builder
public class ImageSearchResultDTO {
    /**
     * The image document containing metadata and content information
     */
    private ImageDocument document;

    /**
     * Display score for API output.
     */
    private Double score;

    /**
     * Raw retrieval score from Elasticsearch. Used for ranking/filter decisions.
     */
    private Double rawScore;

    /**
     * Search cursor for pagination
     */
    private List<Object> sortValues;
}
  
