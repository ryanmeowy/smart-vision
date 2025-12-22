package com.smart.vision.core.strategy;

import com.smart.vision.core.model.enums.StrategyTypeEnum;
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
@Component
public class StrategyFactory {

    private final Map<StrategyTypeEnum, RetrievalStrategy> strategies;

    public StrategyFactory(List<RetrievalStrategy> strategyList) {
        this.strategies = new EnumMap<>(StrategyTypeEnum.class);
        for (RetrievalStrategy strategy : strategyList) {
            this.strategies.put(strategy.getType(), strategy);
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
        StrategyTypeEnum strategyType = StrategyTypeEnum.getByCode(searchType);
        return Optional.ofNullable(strategies.get(strategyType))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No retrieval strategy found for type: " + strategyType));
    }

    /**
     * Get strategy by type with fallback to HYBRID strategy
     *
     * @param strategyType the type of strategy to retrieve
     * @return the retrieval strategy or HYBRID strategy as fallback
     */
    public RetrievalStrategy getStrategyWithFallback(StrategyTypeEnum strategyType) {
        return strategies.getOrDefault(strategyType, strategies.get(StrategyTypeEnum.HYBRID));
    }
}