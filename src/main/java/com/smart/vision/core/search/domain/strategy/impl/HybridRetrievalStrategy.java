package com.smart.vision.core.search.domain.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.integration.ai.port.ContentGenerationService;
import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService;
import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService.RerankResult;
import com.smart.vision.core.search.domain.model.HybridSearchParamDTO;
import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.search.domain.model.StrategyTypeEnum;
import com.smart.vision.core.search.domain.ranking.RrfFusionService;
import com.smart.vision.core.search.domain.strategy.RetrievalStrategy;
import com.smart.vision.core.search.infrastructure.persistence.es.repository.ImageRepository;
import com.smart.vision.core.search.interfaces.rest.dto.SearchQueryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.smart.vision.core.common.constant.EmbeddingConstant.DEFAULT_TOP_K;
import static com.smart.vision.core.common.constant.SearchConstant.DEFAULT_RESULT_LIMIT;

/**
 * Hybrid retrieval strategy implementation that combines multiple search approaches
 * with RRF and cross-encoder rerank.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrievalStrategy implements RetrievalStrategy {

    private final ImageRepository imageRepository;
    private final ContentGenerationService generationService;
    private final RrfFusionService rrfFusionService;
    private final CrossEncoderRerankService crossEncoderRerankService;

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
                .graphTriples(generationService.praseTriplesFromKeyword(query.getKeyword()))
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
        if (!CollectionUtil.isEmpty(queryVector)) {
            rankingLists.add(imageRepository.vectorSearch(queryVector, recallSize));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            rankingLists.add(imageRepository.textSearch(query.getKeyword(), recallSize, enableOcr));
        }
        return rrfFusionService.fuse(rankingLists, recallSize, rrfRankConstant);
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

        List<String> docs = window.stream()
                .map(item -> buildRerankText(item, enableOcr))
                .collect(Collectors.toList());

        List<RerankResult> rerankResults = crossEncoderRerankService.rerank(keyword, docs, docs.size());
        if (CollectionUtil.isEmpty(rerankResults)) {
            return window.stream().limit(limit).collect(Collectors.toList());
        }

        Map<Integer, Double> scoreByIndex = new HashMap<>();
        for (RerankResult rerankResult : rerankResults) {
            if (rerankResult == null) {
                continue;
            }
            int index = rerankResult.index();
            if (index < 0 || index >= window.size()) {
                continue;
            }
            scoreByIndex.put(index, normalizeRerankScore(rerankResult.score()));
        }

        List<Integer> ordered = new ArrayList<>(scoreByIndex.keySet());
        ordered.sort(Comparator.<Integer, Double>comparing(scoreByIndex::get).reversed());
        for (int i = 0; i < window.size(); i++) {
            if (!scoreByIndex.containsKey(i)) {
                ordered.add(i);
            }
        }

        List<ImageSearchResultDTO> reranked = new ArrayList<>(Math.min(limit, ordered.size()));
        for (Integer index : ordered) {
            if (reranked.size() >= limit) {
                break;
            }
            ImageSearchResultDTO dto = window.get(index);
            Double rerankScore = scoreByIndex.get(index);
            if (rerankScore != null) {
                dto.setRawScore(rerankScore);
                dto.setScore(rerankScore);
            }
            reranked.add(dto);
        }
        log.debug("Cross-encoder rerank applied, candidates={}, scored={}, return={}",
                window.size(), scoreByIndex.size(), reranked.size());
        return reranked;
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
}
