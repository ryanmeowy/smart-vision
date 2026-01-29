package com.smart.vision.core.strategy;

import com.smart.vision.core.model.enums.StrategyTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strategy factory for retrieving appropriate retrieval strategies by type
 * This factory provides type-safe access to registered strategies and handles
 * fallback logic when specific strategies are not available.
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Component
public class StrategyFactory {

    private final Map<StrategyTypeEnum, RetrievalStrategy> strategies;

    public StrategyFactory(List<RetrievalStrategy> strategyList) {
        this.strategies = new EnumMap<>(StrategyTypeEnum.class);
        for (RetrievalStrategy strategy : strategyList) {
            this.strategies.put(strategy.getType(), strategy);
            log.info("Search strategy registered: {}", strategy.getType().getDesc());
        }
        validateRequiredStrategies();
    }

    private void validateRequiredStrategies() {
        for (StrategyTypeEnum type : StrategyTypeEnum.values()) {
            if (!strategies.containsKey(type)) {
                log.warn("Missing required search strategy: {}", type.getDesc());
            }
        }
    }

    /**
     * Get strategy by type
     *
     * @param searchType the type of strategy to retrieve
     * @return the retrieval strategy
     * @throws IllegalArgumentException if no strategy found for given type
     */
    public RetrievalStrategy getStrategy(String searchType) {
        try {
            StrategyTypeEnum strategyType = StrategyTypeEnum.getByCode(searchType);
            RetrievalStrategy retrievalStrategy = strategies.get(strategyType);
            if (retrievalStrategy == null) {
                log.warn("No effective search strategy was hit. {}", searchType);
                return strategies.get(StrategyTypeEnum.HYBRID);
            }
            return retrievalStrategy;
        }catch (Exception e) {
            log.error("Failed to get search strategy", e);
            return strategies.get(StrategyTypeEnum.HYBRID);
        }
    }
}