package com.smart.vision.core.search.application.impl;

import com.smart.vision.core.common.constant.EmbeddingConstant;
import com.smart.vision.core.common.exception.ApiError;
import com.smart.vision.core.common.exception.BusinessException;
import com.smart.vision.core.search.application.KbQueryEmbeddingService;
import com.smart.vision.core.search.application.UnifiedSearchService;
import com.smart.vision.core.search.config.AppSearchProperties;
import com.smart.vision.core.search.domain.model.KbSegmentHit;
import com.smart.vision.core.search.domain.port.KbSegmentSearchPort;
import com.smart.vision.core.search.infrastructure.persistence.es.document.KbSegmentDocument;
import com.smart.vision.core.search.interfaces.rest.dto.KbSearchExplainDTO;
import com.smart.vision.core.search.interfaces.rest.dto.KbSearchQueryDTO;
import com.smart.vision.core.search.interfaces.rest.dto.KbSearchResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unified retrieval service over kb_segment index.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedSearchServiceImpl implements UnifiedSearchService {

    private static final String STRATEGY_CODE = "KB_RRF";

    private final KbSegmentSearchPort kbSegmentSearchPort;
    private final KbQueryEmbeddingService kbQueryEmbeddingService;
    private final AppSearchProperties appSearchProperties;

    @Override
    public List<KbSearchResultDTO> search(KbSearchQueryDTO query) {
        if (query == null || !StringUtils.hasText(query.getQuery())) {
            throw new BusinessException(ApiError.INVALID_REQUEST, "query cannot be empty");
        }
        String keyword = query.getQuery().trim();
        int topK = resolveTopK(query.getTopK());
        int limit = resolveLimit(query.getLimit(), topK);

        List<Float> queryVector = kbQueryEmbeddingService.embedQuery(keyword);
        List<KbSegmentHit> textHits = kbSegmentSearchPort.textSearch(keyword, topK);
        List<KbSegmentHit> vectorHits = kbSegmentSearchPort.vectorSearch(queryVector, topK);
        log.info("kb search recall completed, keyword={}, textHits={}, vectorHits={}", keyword, textHits.size(), vectorHits.size());
        return fuseAndAssemble(textHits, vectorHits, keyword, limit, appSearchProperties.getRrf().getRankConstant());
    }

    private List<KbSearchResultDTO> fuseAndAssemble(List<KbSegmentHit> textHits,
                                                    List<KbSegmentHit> vectorHits,
                                                    String keyword,
                                                    int limit,
                                                    int rankConstant) {
        Map<String, Accumulator> grouped = new LinkedHashMap<>();
        ingest(textHits, false, Math.max(1, rankConstant), grouped);
        ingest(vectorHits, true, Math.max(1, rankConstant), grouped);

        return grouped.values().stream()
                .sorted(Comparator.comparingDouble(Accumulator::getRrfScore).reversed()
                        .thenComparing(Comparator.comparingInt(Accumulator::getHitCount).reversed())
                        .thenComparing(Comparator.comparingDouble(Accumulator::getBestRawScore).reversed()))
                .limit(limit)
                .map(acc -> toResult(acc, keyword))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void ingest(List<KbSegmentHit> ranking,
                        boolean vectorRoute,
                        int rankConstant,
                        Map<String, Accumulator> grouped) {
        if (ranking == null || ranking.isEmpty()) {
            return;
        }
        for (int i = 0; i < ranking.size(); i++) {
            KbSegmentHit hit = ranking.get(i);
            KbSegmentDocument doc = hit == null ? null : hit.getDocument();
            String segmentId = doc == null ? null : doc.getSegmentId();
            if (!StringUtils.hasText(segmentId)) {
                continue;
            }
            Accumulator acc = grouped.computeIfAbsent(segmentId, ignored -> new Accumulator());
            acc.rrfScore += reciprocal(rankConstant, i);
            acc.hitCount += 1;
            acc.bestRawScore = Math.max(acc.bestRawScore, hit.getRawScore());
            if (vectorRoute) {
                acc.vectorHit = true;
                if (acc.vectorSource == null) {
                    acc.vectorSource = hit;
                }
                continue;
            }
            if (acc.textSource == null) {
                acc.textSource = hit;
            }
        }
    }

    private double reciprocal(int rankConstant, int rankIndex) {
        return 1d / (rankConstant + rankIndex + 1d);
    }

    private KbSearchResultDTO toResult(Accumulator acc, String keyword) {
        KbSegmentHit displaySource = acc.textSource != null ? acc.textSource : acc.vectorSource;
        KbSegmentDocument doc = displaySource == null ? null : displaySource.getDocument();
        if (doc == null) {
            return null;
        }
        Map<String, String> highlights = acc.textSource == null ? Map.of() : acc.textSource.getHighlights();
        boolean titleHit = highlights.containsKey("title") || containsIgnoreCase(doc.getTitle(), keyword);
        boolean contentHit = highlights.containsKey("contentText") || containsIgnoreCase(doc.getContentText(), keyword);
        boolean ocrHit = highlights.containsKey("ocrText") || containsIgnoreCase(doc.getOcrText(), keyword);

        List<String> hitSources = new ArrayList<>();
        if (acc.vectorHit) {
            hitSources.add("VECTOR");
        }
        if (titleHit) {
            hitSources.add("TITLE");
        }
        if (contentHit) {
            hitSources.add("CONTENT");
        }
        if (ocrHit) {
            hitSources.add("OCR");
        }

        String snippet = pickSnippet(doc, highlights);
        return KbSearchResultDTO.builder()
                .resultType(doc.getSegmentType())
                .assetType(doc.getAssetType())
                .snippet(snippet)
                .pageNo(doc.getPageNo())
                .score(acc.rrfScore)
                .segmentId(doc.getSegmentId())
                .assetId(doc.getAssetId())
                .sourceRef(doc.getSourceRef())
                .explain(KbSearchExplainDTO.builder()
                        .strategyEffective(STRATEGY_CODE)
                        .hitSources(hitSources)
                        .matchedBy(KbSearchExplainDTO.MatchedBy.builder()
                                .vector(acc.vectorHit)
                                .title(titleHit)
                                .content(contentHit)
                                .ocr(ocrHit)
                                .build())
                        .build())
                .build();
    }

    private String pickSnippet(KbSegmentDocument doc, Map<String, String> highlights) {
        if (highlights != null && StringUtils.hasText(highlights.get("contentText"))) {
            return highlights.get("contentText");
        }
        if (highlights != null && StringUtils.hasText(highlights.get("ocrText"))) {
            return highlights.get("ocrText");
        }
        if (highlights != null && StringUtils.hasText(highlights.get("title"))) {
            return highlights.get("title");
        }
        if (StringUtils.hasText(doc.getContentText())) {
            return clip(doc.getContentText(), 180);
        }
        if (StringUtils.hasText(doc.getOcrText())) {
            return clip(doc.getOcrText(), 180);
        }
        if (StringUtils.hasText(doc.getTitle())) {
            return clip(doc.getTitle(), 180);
        }
        return "";
    }

    private String clip(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return StringUtils.hasText(text)
                && StringUtils.hasText(keyword)
                && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private int resolveTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return EmbeddingConstant.DEFAULT_TOP_K;
        }
        return Math.min(topK, 200);
    }

    private int resolveLimit(Integer limit, int topK) {
        if (limit == null || limit <= 0) {
            return topK;
        }
        return Math.min(limit, 200);
    }

    private static final class Accumulator {
        private double rrfScore;
        private int hitCount;
        private double bestRawScore;
        private boolean vectorHit;
        private KbSegmentHit vectorSource;
        private KbSegmentHit textSource;

        private Accumulator() {
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
