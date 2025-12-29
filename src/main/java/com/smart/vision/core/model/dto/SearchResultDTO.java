
package com.smart.vision.core.model.dto;

import co.elastic.clients.elasticsearch._types.FieldValue;
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
    private List<FieldValue> sortValues;

    /**
     * AI tags
     */
    private List<String> tags;
}