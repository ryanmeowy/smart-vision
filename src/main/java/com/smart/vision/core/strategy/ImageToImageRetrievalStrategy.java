package com.smart.vision.core.strategy;

import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.enums.StrategyTypeEnum;
import com.smart.vision.core.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.smart.vision.core.constant.CommonConstant.IMAGE_TO_IMAGE_TOP_K;

@Component
@RequiredArgsConstructor
public class ImageToImageRetrievalStrategy implements RetrievalStrategy {

    private final ImageRepository imageRepository;

    /**
     * Execute search operation using the specific strategy implementation
     * This method performs the actual search logic based on the provided query parameters
     * and optional vector representation of the user's search query.
     *
     * @param query       search request
     * @param queryVector vector representation of user query (maybe null)
     * @return list of matched documents
     */
    @Override
    public List<ImageSearchResultDTO> search(SearchQueryDTO query, List<Float> queryVector) {
        Integer topK = null == query ? IMAGE_TO_IMAGE_TOP_K : null == query.getTopK() ? IMAGE_TO_IMAGE_TOP_K : query.getTopK();
        return imageRepository.searchSimilar(queryVector, topK, "0");
    }

    /**
     * Get the strategy type identifier that this implementation represents
     * This method allows the strategy factory or manager to identify and select
     * the appropriate strategy implementation based on the requested search type.
     *
     * @return strategy type
     */
    @Override
    public StrategyTypeEnum getType() {
        return StrategyTypeEnum.IMAGE_TO_IMAGE;
    }
}
