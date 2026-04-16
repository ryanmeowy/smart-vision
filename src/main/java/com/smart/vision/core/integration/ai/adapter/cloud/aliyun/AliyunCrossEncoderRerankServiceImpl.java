package com.smart.vision.core.integration.ai.adapter.cloud.aliyun;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import com.smart.vision.core.common.exception.ApiError;
import com.smart.vision.core.common.exception.BusinessException;
import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.smart.vision.core.integration.constant.AliyunConstant.BAILIAN_API_KEY_ENV_NAME;
import static com.smart.vision.core.integration.constant.AliyunConstant.RERANK_MODEL_NAME;

/**
 * DashScope text-rerank implementation based on cross-encoder model.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.capability-provider", name = "rerank", havingValue = "aliyun")
public class AliyunCrossEncoderRerankServiceImpl implements CrossEncoderRerankService {

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, Integer topN) {
        if (!StringUtils.hasText(query) || CollectionUtil.isEmpty(documents)) {
            return List.of();
        }
        int safeTopN = topN == null || topN <= 0 ? documents.size() : Math.min(topN, documents.size());
        try {
            TextReRankParam param = TextReRankParam.builder()
                    .apiKey(resolveApiKey())
                    .model(RERANK_MODEL_NAME)
                    .query(query)
                    .documents(documents)
                    .topN(safeTopN)
                    .returnDocuments(false)
                    .build();
            TextReRankResult result = new TextReRank().call(param);
            if (result.getOutput() == null || CollectionUtil.isEmpty(result.getOutput().getResults())) {
                return List.of();
            }
            return result.getOutput().getResults().stream()
                    .filter(item -> item != null && item.getIndex() != null)
                    .map(this::toRerankResult)
                    .sorted(Comparator.comparingDouble(RerankResult::score).reversed())
                    .toList();
        } catch (NoApiKeyException e) {
            log.error("Cross-encoder rerank failed: missing DashScope API key", e);
        } catch (InputRequiredException e) {
            log.warn("Cross-encoder rerank skipped due to invalid input", e);
        } catch (ApiException e) {
            log.error("Cross-encoder rerank API call failed", e);
        } catch (Exception e) {
            log.error("Cross-encoder rerank failed", e);
        }
        return List.of();
    }

    private RerankResult toRerankResult(TextReRankOutput.Result item) {
        double score = item.getRelevanceScore() == null ? 0d : item.getRelevanceScore();
        return new RerankResult(item.getIndex(), Math.max(0d, score));
    }

    private String resolveApiKey() {
        return Optional.ofNullable(System.getenv(BAILIAN_API_KEY_ENV_NAME))
                .orElseThrow(() -> new BusinessException(ApiError.INVALID_API_KEY));
    }
}
