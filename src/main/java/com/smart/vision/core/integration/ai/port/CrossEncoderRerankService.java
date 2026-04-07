package com.smart.vision.core.integration.ai.port;

import java.util.List;

/**
 * Cross-encoder rerank service abstraction.
 */
public interface CrossEncoderRerankService {

    /**
     * Rerank query-document pairs and return ranked indexes with relevance score.
     *
     * @param query user query
     * @param documents candidate documents
     * @param topN number of ranked candidates expected
     * @return rerank results in descending order
     */
    List<RerankResult> rerank(String query, List<String> documents, Integer topN);

    /**
     * Rerank result tuple.
     */
    record RerankResult(int index, double score) {}
}
