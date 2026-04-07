package com.smart.vision.core.search.domain.ranking;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.smart.vision.core.search.domain.util.ScoreUtil.mapScoreToPercentage;

/**
 * Reciprocal Rank Fusion for multi-retriever result merging.
 */
@Component
public class RrfFusionService {

    public List<ImageSearchResultDTO> fuse(List<List<ImageSearchResultDTO>> rankingLists,
                                           int limit,
                                           int rankConstant) {
        if (CollectionUtil.isEmpty(rankingLists)) {
            return List.of();
        }
        int safeLimit = Math.max(1, limit);
        int safeRankConstant = Math.max(1, rankConstant);

        Map<Long, Accumulator> grouped = new HashMap<>();
        for (List<ImageSearchResultDTO> rankingList : rankingLists) {
            if (CollectionUtil.isEmpty(rankingList)) {
                continue;
            }
            for (int i = 0; i < rankingList.size(); i++) {
                ImageSearchResultDTO hit = rankingList.get(i);
                ImageDocument doc = hit == null ? null : hit.getDocument();
                Long id = doc == null ? null : doc.getId();
                if (id == null) {
                    continue;
                }
                Accumulator accumulator = grouped.computeIfAbsent(id, ignored -> new Accumulator(id));
                double reciprocal = 1d / (safeRankConstant + i + 1d);
                accumulator.rrfScore += reciprocal;
                accumulator.hitCount += 1;

                double currentDecisionScore = decisionScore(hit);
                if (accumulator.primary == null || currentDecisionScore > accumulator.bestRawScore) {
                    accumulator.primary = hit;
                    accumulator.bestRawScore = currentDecisionScore;
                }
            }
        }

        List<Accumulator> accumulators = new ArrayList<>(grouped.values());
        accumulators.sort(
                Comparator.comparingDouble(Accumulator::getRrfScore).reversed()
                        .thenComparing(Comparator.comparingInt(Accumulator::getHitCount).reversed())
                        .thenComparing(Comparator.comparingDouble(Accumulator::getBestRawScore).reversed())
                        .thenComparingLong(Accumulator::getId)
        );

        List<ImageSearchResultDTO> fused = new ArrayList<>(Math.min(safeLimit, accumulators.size()));
        for (Accumulator accumulator : accumulators) {
            if (fused.size() >= safeLimit) {
                break;
            }
            if (accumulator.primary == null || accumulator.primary.getDocument() == null) {
                continue;
            }
            double rawScore = accumulator.bestRawScore > 0 ? accumulator.bestRawScore : accumulator.rrfScore;
            fused.add(ImageSearchResultDTO.builder()
                    .document(accumulator.primary.getDocument())
                    .rawScore(rawScore)
                    .score(mapScoreToPercentage(rawScore))
                    .sortValues(accumulator.primary.getSortValues())
                    .build());
        }
        return fused;
    }

    private double decisionScore(ImageSearchResultDTO result) {
        if (result == null) {
            return 0d;
        }
        if (result.getRawScore() != null) {
            return result.getRawScore();
        }
        if (result.getScore() != null) {
            return result.getScore();
        }
        return 0d;
    }

    private static final class Accumulator {
        private final long id;
        private double rrfScore;
        private int hitCount;
        private double bestRawScore;
        private ImageSearchResultDTO primary;

        private Accumulator(long id) {
            this.id = id;
        }

        private long getId() {
            return id;
        }

        private double getRrfScore() {
            return rrfScore;
        }

        private int getHitCount() {
            return hitCount;
        }

        private double getBestRawScore() {
            return bestRawScore;
        }
    }
}
