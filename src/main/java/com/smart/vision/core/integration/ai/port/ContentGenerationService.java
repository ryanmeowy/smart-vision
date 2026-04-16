package com.smart.vision.core.integration.ai.port;

import com.smart.vision.core.common.model.GraphTriple;

import java.util.List;

/**
 * AIGC Generation Service Interface
 */
public interface ContentGenerationService {

    /**
     * Generate one-shot summary for the image.
     * @param imageUrl Image URL
     * @return summary text
     */
    String generateSummary(String imageUrl);

    /**
     * Generate a unique file name for the image
     * @param imageUrl Image URL
     * @return Unique file name
     */
    String generateFileName(String imageUrl);

    /**
     * Generate tags for the image
     * @param imageUrl Image URL
     * @return List of tags
     */
    List<String> generateTags(String imageUrl);

    /**
     * Generate graph for the image
     * @param imageUrl Image URL
     * @return List of graph triples
     */
    List<GraphTriple> generateGraph(String imageUrl);

    /**
     * Parse graph triples from keyword
     * @param keyword Keyword
     * @return List of graph triples
     */
    List<GraphTriple> praseTriplesFromKeyword(String keyword);
}
