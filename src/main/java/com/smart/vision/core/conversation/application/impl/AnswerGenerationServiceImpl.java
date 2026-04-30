package com.smart.vision.core.conversation.application.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.vision.core.conversation.application.AnswerGenerationService;
import com.smart.vision.core.conversation.application.model.AnswerGenerationResult;
import com.smart.vision.core.conversation.domain.model.ConversationCitation;
import com.smart.vision.core.conversation.domain.port.ConversationRewritePort;
import com.smart.vision.core.search.interfaces.rest.dto.KbSearchResultDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default grounded answer generation service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerGenerationServiceImpl implements AnswerGenerationService {

    private static final int GROUNDING_LIMIT = 5;
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*(\\{[\\s\\S]*?})\\s*```");

    private final ConversationRewritePort conversationRewritePort;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Override
    public AnswerGenerationResult generate(String userQuery,
                                           String rewrittenQuery,
                                           List<KbSearchResultDTO> topCandidates,
                                           List<ConversationCitation> citations) {
        meterRegistry.counter("answer.generate.count").increment();
        Timer.Sample sample = Timer.start(meterRegistry);
        List<GroundingSegment> groundingSegments = pickGroundingSegments(topCandidates, citations);
        try {
            if (groundingSegments.isEmpty()) {
                meterRegistry.counter("answer.generate.fallback.count").increment();
                meterRegistry.counter("no_evidence.answer.rate").increment();
                return buildNoEvidenceFallback();
            }

            String prompt = buildPrompt(userQuery, rewrittenQuery, groundingSegments);
            String rawText = conversationRewritePort.generateText(prompt);
            String answerText = parseAnswer(rawText);
            if (!StringUtils.hasText(answerText)) {
                meterRegistry.counter("answer.generate.fallback.count").increment();
                return buildModelFallback(groundingSegments, "empty_model_answer");
            }
            AnswerGenerationResult result = new AnswerGenerationResult();
            result.setAnswerText(answerText.trim());
            result.setFallbackUsed(false);
            result.setFallbackReason(null);
            result.setAnswerInputSegmentIds(collectSegmentIds(groundingSegments));
            return result;
        } catch (Exception e) {
            log.warn("Answer generation failed: {}", e.getMessage());
            meterRegistry.counter("answer.generate.fallback.count").increment();
            return buildModelFallback(groundingSegments, "model_unavailable");
        } finally {
            sample.stop(Timer.builder("answer.generate.latency")
                    .description("Conversation answer generation latency.")
                    .register(meterRegistry));
        }
    }

    private List<GroundingSegment> pickGroundingSegments(List<KbSearchResultDTO> topCandidates, List<ConversationCitation> citations) {
        if (topCandidates == null || topCandidates.isEmpty() || citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<GroundingSegment> segments = new ArrayList<>();
        int limit = Math.min(Math.min(topCandidates.size(), citations.size()), GROUNDING_LIMIT);
        for (int i = 0; i < limit; i++) {
            KbSearchResultDTO candidate = topCandidates.get(i);
            ConversationCitation citation = citations.get(i);
            if (candidate == null || citation == null) {
                continue;
            }
            String evidence = resolveEvidence(candidate, citation);
            if (!StringUtils.hasText(evidence)) {
                continue;
            }
            GroundingSegment segment = new GroundingSegment();
            segment.index = i + 1;
            segment.hitType = citation.getHitType();
            segment.fileName = citation.getFileName();
            segment.pageNo = citation.getPageNo();
            segment.segmentId = citation.getSegmentId();
            segment.evidence = evidence;
            segments.add(segment);
        }
        return segments;
    }

    private String resolveEvidence(KbSearchResultDTO candidate, ConversationCitation citation) {
        if (StringUtils.hasText(citation.getSnippet())) {
            return citation.getSnippet().trim();
        }
        if (StringUtils.hasText(candidate.getSnippet())) {
            return candidate.getSnippet().trim();
        }
        if (candidate.getTopChunks() != null && !candidate.getTopChunks().isEmpty()) {
            KbSearchResultDTO.TopChunk first = candidate.getTopChunks().getFirst();
            if (first != null && StringUtils.hasText(first.getSnippet())) {
                return first.getSnippet().trim();
            }
        }
        return null;
    }

    private String buildPrompt(String userQuery, String rewrittenQuery, List<GroundingSegment> segments) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是知识库问答助手。");
        builder.append("只能基于给定证据回答，不得编造。");
        builder.append("输出 JSON：{\"answer\":\"string\"}。");
        builder.append("回答风格：先给简要结论，再给2-4条要点，结尾保留“参考来源”。");
        builder.append("引用格式：必须使用 [1] [2] 这种编号，且编号只能引用给定证据。");
        builder.append("如果证据不足，请直接回答“未找到足够内容支持该问题”。");
        builder.append("用户问题：").append(userQuery).append("。");
        builder.append("检索改写：").append(rewrittenQuery).append("。");
        builder.append("证据列表：");
        for (GroundingSegment segment : segments) {
            builder.append("[")
                    .append(segment.index)
                    .append("] file=")
                    .append(segment.fileName)
                    .append(",page=")
                    .append(segment.pageNo == null ? "NA" : segment.pageNo)
                    .append(",type=")
                    .append(segment.hitType)
                    .append(",snippet=")
                    .append(segment.evidence)
                    .append(";");
        }
        return builder.toString();
    }

    private String parseAnswer(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        String json = extractJson(rawText.trim());
        if (!StringUtils.hasText(json)) {
            return rawText.trim();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String answer = root.path("answer").asText(null);
            return StringUtils.hasText(answer) ? answer.trim() : null;
        } catch (Exception e) {
            return rawText.trim();
        }
    }

    private String extractJson(String text) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }
        return null;
    }

    private AnswerGenerationResult buildNoEvidenceFallback() {
        AnswerGenerationResult result = new AnswerGenerationResult();
        result.setAnswerText("未找到足够内容支持该问题。请尝试缩小范围或补充关键词。");
        result.setFallbackUsed(true);
        result.setFallbackReason("no_evidence");
        result.setAnswerInputSegmentIds(List.of());
        return result;
    }

    private AnswerGenerationResult buildModelFallback(List<GroundingSegment> segments, String reason) {
        StringBuilder answer = new StringBuilder();
        answer.append("根据当前知识库，先给出可确认的信息：");
        for (GroundingSegment segment : segments) {
            answer.append(System.lineSeparator())
                    .append("- [")
                    .append(segment.index)
                    .append("] ")
                    .append(segment.evidence);
        }
        answer.append(System.lineSeparator()).append("如需更精确答案，请继续追问。");
        AnswerGenerationResult result = new AnswerGenerationResult();
        result.setAnswerText(answer.toString());
        result.setFallbackUsed(true);
        result.setFallbackReason(reason);
        result.setAnswerInputSegmentIds(collectSegmentIds(segments));
        return result;
    }

    private List<String> collectSegmentIds(List<GroundingSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        return segments.stream()
                .map(segment -> segment.segmentId)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static class GroundingSegment {
        private int index;
        private String fileName;
        private Integer pageNo;
        private String hitType;
        private String segmentId;
        private String evidence;
    }
}
