package com.smart.vision.core.model.dto;

import com.smart.vision.core.model.entity.ImageDocument;
import lombok.Builder;
import lombok.Data;

/**
 * Image search result model
 *
 * @author ryan
 * @since 2025/12/23
 */
@Data
@Builder
public class ImageSearchResult {
    /**
     * The image document containing metadata and content information
     */
    private ImageDocument document;

    /**
     * The similarity score between the search query and this image result
     */
    private Double score;
}
  
