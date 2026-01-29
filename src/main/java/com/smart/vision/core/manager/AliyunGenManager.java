package com.smart.vision.core.manager;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.aliyun.core.utils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.smart.vision.core.model.dto.GraphTripleDTO;
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

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.smart.vision.core.constant.AliyunConstant.IMAGE_GEN_MODEL_NAME;
import static com.smart.vision.core.constant.AliyunConstant.TEXT_MODEL_NAME;
import static com.smart.vision.core.constant.AliyunConstant.VISION_MODEL_NAME;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_IMAGE_NAME;
import static com.smart.vision.core.constant.CommonConstant.SSE_TIMEOUT;
import static com.smart.vision.core.constant.ValidationConstant.AI_RESPONSE_REGEX;
import static com.smart.vision.core.constant.ValidationConstant.MD_JSON_REGEX;
import static com.smart.vision.core.model.enums.PromptEnum.GRAPH_IMAGE;
import static com.smart.vision.core.model.enums.PromptEnum.GRAPH_TEXT;
import static com.smart.vision.core.model.enums.PromptEnum.TAG_GEN;
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
    private final Gson gson;

    private static final Pattern TEXT_PATTERN = Pattern.compile(AI_RESPONSE_REGEX);

    private final Pattern MD_JSON_PATTERN = Pattern.compile(MD_JSON_REGEX, Pattern.DOTALL);

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
    public String genFileName(String imageUrl) {
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
            String rawName = Optional.ofNullable(callResult)
                    .map(MultiModalConversationResult::getOutput)
                    .map(MultiModalConversationOutput::getChoices)
                    .filter(CollectionUtil::isNotEmpty)
                    .map(List::getFirst)
                    .map(MultiModalConversationOutput.Choice::getMessage)
                    .map(MultiModalMessage::getContent)
                    .map(Object::toString)
                    .orElse(Strings.EMPTY);
            Matcher matcher = TEXT_PATTERN.matcher(rawName);
            return matcher.find() ? matcher.group(1) : DEFAULT_IMAGE_NAME;
        } catch (NoApiKeyException e) {
            log.error("API Key is not configured: {}", e.getMessage());
        } catch (UploadFileException e) {
            log.error("File upload failed: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Generation failed: {}", e.getMessage());
        }
        return DEFAULT_IMAGE_NAME;
    }

    /**
     * Call VL model to generate tags
     */
    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<String> generateTags(String imageUrl) {
        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role("user")
                .content(Arrays.asList(
                        Map.of("image", imageUrl),
                        Map.of("text", TAG_GEN.getPrompt())
                ))
                .build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .model(VISION_MODEL_NAME)
                .message(userMessage)
                .apiKey(apiKey)
                .build();

        MultiModalConversation conv = new MultiModalConversation();
        try {
            MultiModalConversationResult result = conv.call(param);
            String content = Optional.ofNullable(result)
                    .map(MultiModalConversationResult::getOutput)
                    .map(MultiModalConversationOutput::getChoices)
                    .filter(CollectionUtil::isNotEmpty)
                    .map(List::getFirst)
                    .map(MultiModalConversationOutput.Choice::getMessage)
                    .map(MultiModalMessage::getContent)
                    .map(Object::toString)
                    .orElse(Strings.EMPTY);
            return parseMdJson(content, String.class);
        } catch (NoApiKeyException e) {
            log.error("API Key is not configured");
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("AI tagging failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<GraphTripleDTO> generateGraph(String imageUrl) {
        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role("user")
                .content(Arrays.asList(Map.of("image", imageUrl), Map.of("text", GRAPH_IMAGE.getPrompt())))
                .build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .model(VISION_MODEL_NAME)
                .message(userMessage)
                .apiKey(apiKey)
                .build();
        MultiModalConversation conv = new MultiModalConversation();
        try {
            MultiModalConversationResult result = conv.call(param);
            String content = Optional.ofNullable(result)
                    .map(MultiModalConversationResult::getOutput)
                    .map(MultiModalConversationOutput::getChoices)
                    .filter(CollectionUtil::isNotEmpty)
                    .map(List::getFirst)
                    .map(MultiModalConversationOutput.Choice::getMessage)
                    .map(MultiModalMessage::getContent)
                    .map(Object::toString)
                    .orElse(Strings.EMPTY);
            return parseMdJson(content, GraphTripleDTO.class);
        } catch (Exception e) {
            log.error("gen graph failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<GraphTripleDTO> praseTriplesFromKeyword(String keyword) {
        Generation gen = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(GRAPH_TEXT.getPrompt())
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(keyword)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(TEXT_MODEL_NAME)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        try {
            GenerationResult result = gen.call(param);
            String content = Optional.of(result)
                    .map(GenerationResult::getOutput)
                    .map(GenerationOutput::getChoices)
                    .filter(CollectionUtil::isNotEmpty)
                    .map(List::getLast)
                    .map(GenerationOutput.Choice::getMessage)
                    .map(Message::getContent)
                    .orElse(Strings.EMPTY);
            return parseMdJson(content, GraphTripleDTO.class);
        } catch (Exception e) {
            log.error("prase triples from keyword failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private <T> List<T> parseMdJson(String content, Class<T> clazz) {
        if (StringUtils.isBlank(content)) return Collections.emptyList();
        try {
            Matcher matcher = MD_JSON_PATTERN.matcher(content);
            if (matcher.find()) {
                String jsonArray = matcher.group(1);
                Type type = TypeToken.getParameterized(List.class, clazz).getType();
                return gson.fromJson(jsonArray, type);
            }
            return gson.fromJson(content, TypeToken.getParameterized(List.class, clazz).getType());
        } catch (Exception e) {
            log.warn("md json parsing failed, original content: {}", content);
            return Collections.emptyList();
        }
    }
}