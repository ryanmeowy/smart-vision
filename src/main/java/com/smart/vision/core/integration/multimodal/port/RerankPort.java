package com.smart.vision.core.integration.multimodal.port;

import java.util.List;

/**
 * Top-level rerank capability port.
 */
public interface RerankPort {

    List<RerankResult> rerank(String query, List<String> documents, Integer topN);

    record RerankResult(int index, double score) {}
}

