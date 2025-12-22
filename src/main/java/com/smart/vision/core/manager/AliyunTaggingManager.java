package com.smart.vision.core.manager;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.smart.vision.core.constant.CommonConstant.TAG_MODEL_NAME;

/**
 * AliyunTaggingManager is responsible for generating tags for images
 * by calling the VL model and processing the results.
 *
 * @author Ryan
 * @since 2025/12/22
 */
@Slf4j
@Component
public class AliyunTaggingManager {

    public static final String PROMPT = "请分析这张图片，提取 3-5 个核心标签，包含物体、场景、风格。" +
            "请直接返回一个 JSON 字符串数组，不要包含 Markdown 格式或其他废话。" +
            "例如：[\"风景\", \"雪山\", \"日落\"]";

    /**
     * Call VL model to generate tags
     */
    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<String> generateTags(String imageUrl) {
        try {
            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role("user")
                    .content(Arrays.asList(
                            // Image part
                            Map.of("image", imageUrl),
                            // Text Prompt (strict format)
                            Map.of("text", PROMPT)
                    ))
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .model(TAG_MODEL_NAME)
                    .message(userMessage)
                    .build();

            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationResult result = conv.call(param);

            String content = result.getOutput().getChoices().get(0).getMessage().getContent().toString();
            return parseTags(content);

        } catch (NoApiKeyException e) {
            log.error("API Key is not configured");
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("AI tagging failed: {}", e.getMessage());
            return Collections.emptyList(); // Fallback on failure, return empty tags to avoid affecting the main process
        }
    }

    /**
     * Helper method: Extract JSON array from model response
     * Handles cases:
     * 1. Pure JSON: ["A", "B"]
     * 2. Markdown wrapped: ```json ["A", "B"] ```
     */
    private List<String> parseTags(String content) {
        try {
            // Regex to extract [...] part
            Pattern pattern = Pattern.compile("\\[.*?]", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String jsonArray = matcher.group();
                Gson gson = new Gson();
                return gson.fromJson(jsonArray, new TypeToken<List<String>>() {}.getType());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Tag parsing failed, original content: {}", content);
            return Collections.emptyList();
        }
    }
}