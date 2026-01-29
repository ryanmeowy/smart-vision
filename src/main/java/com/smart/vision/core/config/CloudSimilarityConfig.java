package com.smart.vision.core.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.smart.vision.core.constant.EmbeddingConstant.CLOUD_HYBRID_SIMILARITY;
import static com.smart.vision.core.constant.EmbeddingConstant.CLOUD_SIMILAR_SIMILARITY;

@Component
@Profile("cloud")
public class CloudSimilarityConfig implements SimilarityConfig {

    @Override
    public Float forHybridSearch() {
        return CLOUD_HYBRID_SIMILARITY;
    }

    @Override
    public Float forSimilarSearch() {
        return CLOUD_SIMILAR_SIMILARITY;
    }
}
