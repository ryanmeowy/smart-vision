package com.smart.vision.core.integration.ai.adapter.local;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.grpc.VisionProto;
import com.smart.vision.core.grpc.VisionServiceGrpc;
import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Local cross-encoder rerank implementation backed by gRPC Python inference service.
 * Falls back to lexical rerank when gRPC rerank is unavailable.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.capability-provider", name = "rerank", havingValue = "local")
public class LocalCrossEncoderRerankImpl implements CrossEncoderRerankService {

    @SuppressWarnings("unused")
    @GrpcClient("vision-python-service")
    private VisionServiceGrpc.VisionServiceBlockingStub visionStub;

    @Value("${grpc.deadline.rerank-ms:5000}")
    private long rerankDeadlineMs;

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, Integer topN) {
        if (!StringUtils.hasText(query) || CollectionUtil.isEmpty(documents)) {
            return List.of();
        }
        int safeTopN = topN == null || topN <= 0 ? documents.size() : Math.min(topN, documents.size());

        List<RerankResult> grpcResults = rerankByGrpc(query, documents, safeTopN);
        if (!CollectionUtil.isEmpty(grpcResults)) {
            return grpcResults;
        }
        return fallbackLexicalRerank(query, documents, safeTopN);
    }

    private List<RerankResult> rerankByGrpc(String query, List<String> documents, int topN) {
        if (visionStub == null) {
            return List.of();
        }
        long startNs = System.nanoTime();
        try {
            VisionProto.RerankRequest request = VisionProto.RerankRequest.newBuilder()
                    .setQuery(query)
                    .addAllDocuments(documents)
                    .setTopN(topN)
                    .build();
            VisionProto.RerankResponse response = visionStub
                    .withDeadlineAfter(rerankDeadlineMs, TimeUnit.MILLISECONDS)
                    .rerank(request);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            log.debug("gRPC rerank success: deadlineMs={}, elapsedMs={}", rerankDeadlineMs, elapsedMs);

            if (response == null || CollectionUtil.isEmpty(response.getResultsList())) {
                return List.of();
            }
            return response.getResultsList().stream()
                    .map(item -> new RerankResult(item.getIndex(), Math.max(0d, item.getRelevanceScore())))
                    .sorted(Comparator.comparingDouble(RerankResult::score).reversed()
                            .thenComparingInt(RerankResult::index))
                    .limit(topN)
                    .toList();
        } catch (StatusRuntimeException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            log.warn("gRPC rerank failed: statusCode={}, deadlineMs={}, elapsedMs={}",
                    e.getStatus().getCode(), rerankDeadlineMs, elapsedMs);
        } catch (Exception e) {
            log.warn("gRPC rerank failed: {}", e.getMessage());
        }
        return List.of();
    }

    private List<RerankResult> fallbackLexicalRerank(String query, List<String> documents, int topN) {
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
                .limit(topN)
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
