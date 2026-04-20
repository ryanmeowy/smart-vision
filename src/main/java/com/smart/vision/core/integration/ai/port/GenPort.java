package com.smart.vision.core.integration.ai.port;

import com.smart.vision.core.common.model.GraphTriple;

import java.util.List;

/**
 * Top-level generation capability port.
 */
public interface GenPort {

    String generateSummary(String imageUrl);

    String generateFileName(String imageUrl);

    List<String> generateTags(String imageUrl);

    List<GraphTriple> generateGraph(String imageUrl);

    List<GraphTriple> praseTriplesFromKeyword(String keyword);
}

