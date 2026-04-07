package com.smart.vision.core.integration.ai.adapter.local;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Local fallback rerank implementation when cloud cross-encoder is unavailable.
 */
@Service
@Profile("local")
public class LocalCrossEncoderRerankImpl implements CrossEncoderRerankService {

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, Integer topN) {
        if (!StringUtils.hasText(query) || CollectionUtil.isEmpty(documents)) {
            return List.of();
        }
        int safeTopN = topN == null || topN <= 0 ? documents.size() : Math.min(topN, documents.size());
        String normalizedQuery = normalize(query);
        Set<String> queryTokens = tokenize(normalizedQuery);

        List<RerankResult> scored = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            String doc = normalize(documents.get(i));
            double score = lexicalScore(normalizedQuery, queryTokens, doc);
            scored.add(new RerankResult(i, score));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(RerankResult::score).reversed()
                        .thenComparingInt(RerankResult::index))
                .limit(safeTopN)
                .toList();
    }

    private double lexicalScore(String normalizedQuery, Set<String> queryTokens, String doc) {
        if (!StringUtils.hasText(doc)) {
            return 0d;
        }
        double exactMatch = doc.contains(normalizedQuery) ? 0.6d : 0d;
        Set<String> docTokens = tokenize(doc);
        if (queryTokens.isEmpty() || docTokens.isEmpty()) {
            return exactMatch;
        }

        int overlap = 0;
        for (String token : queryTokens) {
            if (docTokens.contains(token)) {
                overlap++;
            }
        }
        double overlapRatio = overlap / (double) queryTokens.size();
        return Math.min(1d, exactMatch + 0.4d * overlapRatio);
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (!StringUtils.hasText(text)) {
            return tokens;
        }
        String[] parts = text.split("[^\\p{L}\\p{N}\\u4e00-\\u9fff]+");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            tokens.add(part);
        }
        return tokens;
    }
}
