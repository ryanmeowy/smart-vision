package com.smart.vision.core.manager;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.smart.vision.core.constant.CommonConstant.VISION_MODEL_NAME;
import static com.smart.vision.core.constant.CommonConstant.TAG_REGEX;
import static com.smart.vision.core.model.enums.PromptEnum.TAG_GEN;

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

    @Value("${DASHSCOPE_API_KEY}")
    private String apiKey;

    private final Pattern TAG_RESULT_PATTERN = Pattern.compile(TAG_REGEX, Pattern.DOTALL);

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
                            Map.of("text", TAG_GEN.getPrompt())
                    ))
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .model(VISION_MODEL_NAME)
                    .message(userMessage)
                    .apiKey(apiKey)
                    .build();

            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationResult result = conv.call(param);

            String content = result.getOutput().getChoices().getFirst().getMessage().getContent().toString();
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
            Matcher matcher = TAG_RESULT_PATTERN.matcher(content);
            if (matcher.find()) {
                String jsonArray = matcher.group(1);
                Gson gson = new Gson();
                return gson.fromJson(jsonArray, new TypeToken<List<String>>() {
                }.getType());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Tag parsing failed, original content: {}", content);
            return Collections.emptyList();
        }
    }
}