package com.smart.vision.core.search.domain.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.search.domain.model.HybridSearchParamDTO;
import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.search.domain.model.StrategyTypeEnum;
import com.smart.vision.core.search.domain.port.QueryGraphParserPort;
import com.smart.vision.core.search.domain.port.SearchRerankPort;
import com.smart.vision.core.search.domain.port.SearchRerankPort.RerankItem;
import com.smart.vision.core.search.domain.ranking.RrfFusionService;
import com.smart.vision.core.search.domain.strategy.RetrievalStrategy;
import com.smart.vision.core.search.infrastructure.persistence.es.repository.ImageRepository;
import com.smart.vision.core.search.interfaces.rest.dto.SearchQueryDTO;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.smart.vision.core.common.constant.EmbeddingConstant.DEFAULT_TOP_K;
import static com.smart.vision.core.common.constant.SearchConstant.DEFAULT_RESULT_LIMIT;
import static com.smart.vision.core.search.domain.util.ScoreUtil.mapScoreToPercentage;

/**
 * Hybrid retrieval strategy implementation that combines multiple search approaches
 * with RRF and cross-encoder rerank.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrievalStrategy implements RetrievalStrategy {

    private final ImageRepository imageRepository;
    private final QueryGraphParserPort queryGraphParserPort;
    private final RrfFusionService rrfFusionService;
    private final SearchRerankPort searchRerankPort;
    private final MeterRegistry meterRegistry;

    @Value("${app.search.rrf.enabled:true}")
    private boolean rrfEnabled;
    @Value("${app.search.rrf.rank-constant:60}")
    private int rrfRankConstant;
    @Value("${app.search.rrf.candidate-multiplier:4}")
    private int rrfCandidateMultiplier;
    @Value("${app.search.rrf.max-candidates:200}")
    private int rrfMaxCandidates;
    @Value("${app.search.rerank.enabled:true}")
    private boolean rerankEnabled;
    @Value("${app.search.rerank.max-doc-chars:1200}")
    private int rerankMaxDocChars;
    @Value("${app.search.rerank.window-enabled:true}")
    private boolean rerankWindowEnabled;
    @Value("${app.search.rerank.window-size:40}")
    private int rerankWindowSize;
    @Value("${app.search.rerank.window-factor:3}")
    private int rerankWindowFactor;
    @Value("${app.search.rerank.window-min:20}")
    private int rerankWindowMin;
    @Value("${app.search.rerank.window-max:80}")
    private int rerankWindowMax;
    @Value("${app.search.rerank.fusion-alpha:0.6}")
    private double rerankFusionAlpha;
    @Value("${app.search.rerank.fusion-beta:0.4}")
    private double rerankFusionBeta;

    @Override
    public List<ImageSearchResultDTO> search(SearchQueryDTO query, List<Float> queryVector) {
        if (query == null) {
            return List.of();
        }
        int requestTopK = query.getTopK() == null ? DEFAULT_TOP_K : query.getTopK();
        int requestLimit = query.getLimit() == null ? DEFAULT_RESULT_LIMIT : query.getLimit();
        boolean enableOcr = query.getEnableOcr() == null || query.getEnableOcr();
        int recallSize = resolveRecallSize(requestTopK, requestLimit);

        HybridSearchParamDTO paramDTO = HybridSearchParamDTO.builder()
                .queryVector(queryVector)
                .topK(recallSize)
                .limit(recallSize)
                .keyword(query.getKeyword())
                .enableOcr(enableOcr)
                .graphTriples(queryGraphParserPort.parseFromKeyword(query.getKeyword()))
                .build();
        List<ImageSearchResultDTO> hybridHits = imageRepository.hybridSearch(paramDTO);

        List<ImageSearchResultDTO> candidates = rrfEnabled
                ? applyRrfFusion(hybridHits, query, queryVector, recallSize, enableOcr)
                : hybridHits;

        return applyCrossEncoderRerank(query.getKeyword(), candidates, requestLimit, enableOcr);
    }

    @Override
    public StrategyTypeEnum getType() {
        return StrategyTypeEnum.HYBRID;
    }

    private int resolveRecallSize(int requestTopK, int requestLimit) {
        int multiplier = Math.max(rrfCandidateMultiplier, 1);
        int recallByLimit = Math.max(1, requestLimit) * multiplier;
        int recallSize = Math.max(Math.max(1, requestTopK), recallByLimit);
        return Math.min(recallSize, Math.max(1, rrfMaxCandidates));
    }

    private List<ImageSearchResultDTO> applyRrfFusion(List<ImageSearchResultDTO> hybridHits,
                                                      SearchQueryDTO query,
                                                      List<Float> queryVector,
                                                      int recallSize,
                                                      boolean enableOcr) {
        List<List<ImageSearchResultDTO>> rankingLists = new ArrayList<>();
        rankingLists.add(hybridHits);
        List<ImageSearchResultDTO> vectorHits = List.of();
        if (!CollectionUtil.isEmpty(queryVector)) {
            vectorHits = imageRepository.vectorSearch(queryVector, recallSize);
            rankingLists.add(vectorHits);
        }
        List<ImageSearchResultDTO> textHits = List.of();
        if (StringUtils.hasText(query.getKeyword())) {
            textHits = imageRepository.textSearch(query.getKeyword(), recallSize, enableOcr);
            rankingLists.add(textHits);
        }
        List<ImageSearchResultDTO> fused = rrfFusionService.fuse(rankingLists, recallSize, rrfRankConstant);
        annotateRecallSources(fused, vectorHits, textHits);
        return fused;
    }

    private List<ImageSearchResultDTO> applyCrossEncoderRerank(String keyword,
                                                               List<ImageSearchResultDTO> candidates,
                                                               int requestLimit,
                                                               boolean enableOcr) {
        if (CollectionUtil.isEmpty(candidates)) {
            return List.of();
        }
        int limit = Math.max(1, requestLimit);
        List<ImageSearchResultDTO> window = new ArrayList<>(candidates);
        if (!rerankEnabled || !StringUtils.hasText(keyword)) {
            return window.stream().limit(limit).collect(Collectors.toList());
        }
        int windowSize = resolveRerankWindowSize(limit, window.size());
        if (windowSize <= 0) {
            return window.stream().limit(limit).collect(Collectors.toList());
        }
        List<ImageSearchResultDTO> rerankWindow = new ArrayList<>(window.subList(0, windowSize));
        List<ImageSearchResultDTO> untouchedTail = windowSize >= window.size()
                ? List.of()
                : window.subList(windowSize, window.size());
        recordWindowMetrics(windowSize, window.size());

        List<String> docs = rerankWindow.stream()
                .map(item -> buildRerankText(item, enableOcr))
                .collect(Collectors.toList());
        Timer.Sample sample = Timer.start(meterRegistry);
        meterRegistry.counter("smartvision.search.rerank.calls").increment();

        List<RerankItem> rerankResults = searchRerankPort.rerank(keyword, docs, docs.size());
        sample.stop(Timer.builder("smartvision.search.rerank.latency")
                .description("Cross-encoder rerank latency")
                .register(meterRegistry));
        if (CollectionUtil.isEmpty(rerankResults)) {
            meterRegistry.counter("smartvision.search.rerank.fallback", "reason", "empty_result").increment();
            return window.stream().limit(limit).collect(Collectors.toList());
        }

        Map<Integer, Double> scoreByIndex = new HashMap<>();
        for (RerankItem rerankResult : rerankResults) {
            if (rerankResult == null) {
                continue;
            }
            int index = rerankResult.index();
            if (index < 0 || index >= rerankWindow.size()) {
                continue;
            }
            scoreByIndex.put(index, normalizeRerankScore(rerankResult.score()));
        }

        WeightPair weights = resolveFusionWeights();
        List<WindowRankItem> rankedWindow = buildAndSortWindow(
                rerankWindow,
                scoreByIndex,
                weights.alpha(),
                weights.beta()
        );
        List<ImageSearchResultDTO> merged = new ArrayList<>(window.size());
        for (WindowRankItem item : rankedWindow) {
            merged.add(item.dto());
        }
        merged.addAll(untouchedTail);

        List<ImageSearchResultDTO> reranked = merged.stream().limit(limit).collect(Collectors.toList());
        log.debug("Cross-encoder rerank applied, candidates={}, window={}, scored={}, return={}, alpha={}, beta={}",
                window.size(), windowSize, scoreByIndex.size(), reranked.size(), weights.alpha(), weights.beta());
        return reranked;
    }

    private void annotateRecallSources(List<ImageSearchResultDTO> fused,
                                       List<ImageSearchResultDTO> vectorHits,
                                       List<ImageSearchResultDTO> textHits) {
        if (CollectionUtil.isEmpty(fused)) {
            return;
        }
        Set<Long> vectorIds = collectDocIds(vectorHits);
        Set<Long> textIds = collectDocIds(textHits);
        for (ImageSearchResultDTO item : fused) {
            if (item == null) {
                continue;
            }
            Long id = item.getDocument() == null ? null : item.getDocument().getId();
            if (id == null) {
                item.setVectorRecallHit(false);
                item.setTextRecallHit(false);
                continue;
            }
            item.setVectorRecallHit(vectorIds.contains(id));
            item.setTextRecallHit(textIds.contains(id));
        }
    }

    private Set<Long> collectDocIds(List<ImageSearchResultDTO> items) {
        Set<Long> ids = new HashSet<>();
        if (CollectionUtil.isEmpty(items)) {
            return ids;
        }
        for (ImageSearchResultDTO item : items) {
            Long id = item == null || item.getDocument() == null ? null : item.getDocument().getId();
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String buildRerankText(ImageSearchResultDTO item, boolean enableOcr) {
        if (item == null || item.getDocument() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(256);
        if (StringUtils.hasText(item.getDocument().getFileName())) {
            sb.append("filename: ").append(item.getDocument().getFileName()).append('\n');
        }
        if (!CollectionUtil.isEmpty(item.getDocument().getTags())) {
            sb.append("tags: ").append(String.join(", ", item.getDocument().getTags())).append('\n');
        }
        if (enableOcr && StringUtils.hasText(item.getDocument().getOcrContent())) {
            sb.append("ocr: ").append(item.getDocument().getOcrContent()).append('\n');
        }
        if (!CollectionUtil.isEmpty(item.getDocument().getRelations())) {
            String relations = item.getDocument().getRelations().stream()
                    .filter(Objects::nonNull)
                    .map(triple -> String.format(Locale.ROOT, "%s-%s-%s",
                            nullToEmpty(triple.getS()),
                            nullToEmpty(triple.getP()),
                            nullToEmpty(triple.getO())))
                    .collect(Collectors.joining("; "));
            if (StringUtils.hasText(relations)) {
                sb.append("relations: ").append(relations);
            }
        }
        String content = sb.toString();
        if (content.length() <= rerankMaxDocChars) {
            return content;
        }
        return content.substring(0, rerankMaxDocChars);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double normalizeRerankScore(Double score) {
        if (score == null || score <= 0) {
            return 0d;
        }
        return Math.min(score, 1d);
    }

    private int resolveRerankWindowSize(int limit, int candidateSize) {
        if (candidateSize <= 0) {
            return 0;
        }
        if (!rerankWindowEnabled) {
            return candidateSize;
        }

        int safeLimit = Math.max(1, limit);
        int base = rerankWindowSize > 0
                ? rerankWindowSize
                : safeLimit * Math.max(1, rerankWindowFactor);
        int lower = Math.max(1, rerankWindowMin);
        int upper = Math.max(lower, rerankWindowMax);
        int sized = Math.max(lower, Math.min(base, upper));
        return Math.min(candidateSize, sized);
    }

    private WeightPair resolveFusionWeights() {
        double alpha = clamp01(rerankFusionAlpha);
        double beta = clamp01(rerankFusionBeta);
        double sum = alpha + beta;
        if (sum <= 0d) {
            return new WeightPair(1d, 0d);
        }
        return new WeightPair(alpha / sum, beta / sum);
    }

    private List<WindowRankItem> buildAndSortWindow(List<ImageSearchResultDTO> rerankWindow,
                                                    Map<Integer, Double> rerankScoreByIndex,
                                                    double alpha,
                                                    double beta) {
        List<WindowRankItem> items = new ArrayList<>(rerankWindow.size());
        for (int i = 0; i < rerankWindow.size(); i++) {
            ImageSearchResultDTO dto = rerankWindow.get(i);
            double retrievalScore = normalizeRetrievalScore(dto);
            double rerankScore = rerankScoreByIndex.getOrDefault(i, 0d);
            double fusionScore = alpha * retrievalScore + beta * rerankScore;
            items.add(new WindowRankItem(i, dto, retrievalScore, rerankScore, fusionScore, extractDocId(dto)));
        }
        items.sort(Comparator
                .comparingDouble(WindowRankItem::fusionScore).reversed()
                .thenComparing(Comparator.comparingDouble(WindowRankItem::retrievalScore).reversed())
                .thenComparingLong(WindowRankItem::docId)
                .thenComparingInt(WindowRankItem::originIndex));
        return items;
    }

    private double normalizeRetrievalScore(ImageSearchResultDTO dto) {
        if (dto == null) {
            return 0d;
        }
        if (dto.getScore() != null) {
            return clamp01(dto.getScore());
        }
        return clamp01(mapScoreToPercentage(dto.getRawScore()));
    }

    private long extractDocId(ImageSearchResultDTO dto) {
        if (dto == null || dto.getDocument() == null || dto.getDocument().getId() == null) {
            return Long.MAX_VALUE;
        }
        return dto.getDocument().getId();
    }

    private void recordWindowMetrics(int windowSize, int candidateSize) {
        DistributionSummary.builder("smartvision.search.rerank.window.size")
                .description("Rerank window size")
                .register(meterRegistry)
                .record(windowSize);

        double ratio = candidateSize <= 0 ? 0d : (double) windowSize / (double) candidateSize;
        DistributionSummary.builder("smartvision.search.rerank.window.ratio")
                .description("Rerank window size / candidate size ratio")
                .register(meterRegistry)
                .record(ratio);

        meterRegistry.counter(
                        "smartvision.search.rerank.window.hit",
                        "hit", String.valueOf(windowSize < candidateSize))
                .increment();
    }

    private static double clamp01(double value) {
        if (value <= 0d) {
            return 0d;
        }
        if (value >= 1d) {
            return 1d;
        }
        return value;
    }

    private record WeightPair(double alpha, double beta) {}

    private record WindowRankItem(int originIndex,
                                  ImageSearchResultDTO dto,
                                  double retrievalScore,
                                  double rerankScore,
                                  double fusionScore,
                                  long docId) {}
}
