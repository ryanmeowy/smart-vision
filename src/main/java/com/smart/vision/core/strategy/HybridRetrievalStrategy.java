
package com.smart.vision.core.strategy;

import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.model.enums.StrategyType;
import com.smart.vision.core.repository.ImageRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HybridRetrievalStrategy implements RetrievalStrategy {

    @Resource
    private ImageRepository imageRepository;

    @Override
    public List<ImageDocument> search(SearchQueryDTO query, List<Float> queryVector) {
        return imageRepository.hybridSearch(query, queryVector);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.HYBRID;
    }
}