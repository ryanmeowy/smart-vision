
package com.smart.vision.core.search.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * image search result model
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Data
@Builder
public class SearchResultDTO implements Serializable {
    /**
     * image ID (ES Doc ID)
     */
    private String id;

    /**
     * image access address (OSS URL)
     */
    private String url;

    /**
     * match score (0.0 - 1.0), higher score means more relevant
     */
    private Double score;

    /**
     * OCR text contained in the image (if any)
     */
    private String ocrText;

    /**
     * highlight (optional, used to display matched text fragments)
     */
    private String highlight;

    /**
     * image filename
     */
    private String filename;

    /**
     * Search cursor for pagination
     */
    private List<Object> sortValues;

    /**
     * AI tags
     */
    private List<String> tags;

    /**
     * Graph triples (subject, predicate, object)
     */
    private List<GraphTripleDTO> relations;

    /**
     * Vector hit status (tri-state): VECTOR_ONLY_LIKE / VECTOR_AND_TEXT / TEXT_ONLY.
     */
    private String vectorHitStatus;

    /**
     * Structured explain info for interview/demo oriented observability.
     */
    private SearchExplainDTO explain;
}
