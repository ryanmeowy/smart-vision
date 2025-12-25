package com.smart.vision.core.manager;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import io.reactivex.Flowable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.smart.vision.core.constant.CommonConstant.IMAGE_GEN_MODEL_NAME;
import static com.smart.vision.core.constant.CommonConstant.SSE_TIMEOUT;
import static com.smart.vision.core.model.enums.ImageGenStyleEnum.getPromptByType;

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

                // Call streaming API
                MultiModalConversation conv = new MultiModalConversation();
                Flowable<MultiModalConversationResult> flowable = conv.streamCall(param);

                // Subscribe to the stream and send to SSE
                flowable.blockingForEach(result -> {
                    String delta = result.getOutput().getChoices().getFirst().getMessage().getContent().toString();
                    if (delta != null && !delta.isEmpty()) {
                        // Send data chunk
                        emitter.send(SseEmitter.event().data(delta));
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
}