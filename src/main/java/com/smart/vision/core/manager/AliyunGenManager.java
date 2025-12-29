package com.smart.vision.core.manager;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.smart.vision.core.model.enums.PromptEnum;
import io.reactivex.Flowable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.smart.vision.core.constant.CommonConstant.*;
import static com.smart.vision.core.model.enums.PromptEnum.getPromptByType;

/**
 * AliyunGenManager is a management class for handling operations related to Aliyun generation.
 * This class primarily facilitates image generation through Aliyun multimodal conversation API.
 *
 * @author Ryan
 * @since 2025/12/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunGenManager {

    @Value("${DASHSCOPE_API_KEY}")
    private String apiKey;
    private final Executor imageGenTaskExecutor;

    private static final Pattern TEXT_PATTERN = Pattern.compile(AI_RESPONSE_REGEX);

    public SseEmitter streamGenerateCopy(String imageUrl, String promptType) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        imageGenTaskExecutor.execute(() -> {
            try {
                String systemPrompt = getPromptByType(promptType);
                MultiModalMessage userMessage = MultiModalMessage.builder()
                        .role(Role.USER.getValue())
                        .content(Arrays.asList(Map.of("image", imageUrl), Map.of("text", systemPrompt)))
                        .build();
                MultiModalConversationParam param = MultiModalConversationParam.builder()
                        .model(IMAGE_GEN_MODEL_NAME)
                        .message(userMessage)
                        .enableSearch(true) // Allow online knowledge search
                        .incrementalOutput(true) // Enable incremental output mode
                        .apiKey(apiKey)
                        .build();

                MultiModalConversation conv = new MultiModalConversation();
                Flowable<MultiModalConversationResult> flowable = conv.streamCall(param);

                // Subscribe to the stream and send to SSE
                flowable.blockingForEach(result -> {
                    String rawData = result.getOutput().getChoices().getFirst().getMessage().getContent().toString();
                    if (rawData != null && !rawData.isEmpty()) {
                        Matcher matcher = TEXT_PATTERN.matcher(rawData);
                        if (matcher.find()) {
                            emitter.send(SseEmitter.event().data(matcher.group(1)));
                        }
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming generation failed", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String GenFileName(String imageUrl) {
        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Arrays.asList(Map.of("image", imageUrl), Map.of("text", PromptEnum.NAME_GEN.getPrompt())))
                .build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .model(IMAGE_GEN_MODEL_NAME)
                .message(userMessage)
                .apiKey(apiKey)
                .build();

        MultiModalConversation conv = new MultiModalConversation();
        try {
            MultiModalConversationResult callResult = conv.call(param);
            String rawName = callResult.getOutput().getChoices().getFirst().getMessage().getContent().toString();
            Matcher matcher = TEXT_PATTERN.matcher(rawName);
            return matcher.find() ? matcher.group(1) : Strings.EMPTY;
        } catch (NoApiKeyException e) {
            log.error("API Key is not configured: {}", e.getMessage());
            return Strings.EMPTY;
        } catch (UploadFileException e) {
            log.error("File upload failed: {}", e.getMessage());
            return Strings.EMPTY;
        } catch (Exception e) {
            log.error("Generation failed: {}", e.getMessage());
            return Strings.EMPTY;
        }
    }
}