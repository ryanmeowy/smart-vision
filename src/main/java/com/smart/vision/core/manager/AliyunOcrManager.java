package com.smart.vision.core.manager;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralRequest;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralResponse;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.CommonConstant.*;
import static com.smart.vision.core.model.enums.PromptEnum.OCR;

/**
 * Aliyun OCR Service
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunOcrManager {

    private final Client client;
    private final Gson gson;
    @Value("${DASHSCOPE_API_KEY}")
    private String apiKey;

    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);
    private static final Pattern SINGLE_LETTER_PATTERN = Pattern.compile(SINGLE_LETTER_REGEX);
    private static final Pattern DIGIT_PATTERN = Pattern.compile(DIGIT_REGEX);
    private static final Pattern TEXT_PATTERN = Pattern.compile(AI_RESPONSE_REGEX);


    /**
     * Extract text from image
     *
     * @param imageUrl Public URL of the image (must be an OSS signed URL)
     * @return Extracted plain text content
     */
    @Deprecated
    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String extractText(String imageUrl) throws Exception {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        RecognizeGeneralRequest request = new RecognizeGeneralRequest();
        request.setUrl(imageUrl);
        RecognizeGeneralResponse response = client.recognizeGeneral(request);
        return parseOcrResult(response);
    }

    public String parseOcrResult(RecognizeGeneralResponse response) {
        if (response == null || response.getBody() == null || response.getBody().getData() == null) {
            return null;
        }
        OrcResult orcResult = gson.fromJson(response.getBody().getData(), OrcResult.class);
        if (orcResult == null || CollectionUtils.isEmpty(orcResult.getPrismWordsInfo()) || orcResult.getOrgHeight() == null) {
            return null;
        }
        return orcResult.getPrismWordsInfo().stream()
                .filter(Objects::nonNull)
                .filter(w -> null != w.getProb())
                .filter(w -> w.getProb() >= 90)
                .filter(w -> null != w.getWord())
                .filter(w -> null != w.getHeight())
                .filter(w -> !URL_PATTERN.matcher(w.getWord()).matches())
                .map(w -> w.getWord().replaceAll(PUNCTUATION_REGEX, ""))
                .map(w -> w.replaceAll(WHITE_SPACE_REGEX, ""))
                .filter(w -> !SINGLE_LETTER_PATTERN.matcher(w).matches())
                .filter(w -> !DIGIT_PATTERN.matcher(w).matches())
                .collect(Collectors.joining());
    }

    @Recover
    public String recover(Exception e, String imageUrl) throws Exception {
        log.error("After retrying 3 times, the OCR service still failed.: {}", imageUrl);
        throw e;
    }

    @Retryable(retryFor = {RuntimeException.class}, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String fetchOcrContent(String imageUrl) {
        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role("user")
                .content(Arrays.asList(
                        Map.of("image", imageUrl),
                        Map.of("text", OCR)
                ))
                .build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .model(VISION_MODEL_NAME)
                .message(userMessage)
                .apiKey(apiKey)
                .build();
        MultiModalConversationResult result;
        try {
            MultiModalConversation conv = new MultiModalConversation();
            result = conv.call(param);
        } catch (NoApiKeyException e) {
            log.error("API Key is not configured");
            return null;
        } catch (Exception e) {
            log.warn("Fetch ocr content failed: {}", e.getMessage());
            return null;
        }
        String content = Optional.ofNullable(result)
                .map(x -> x.getOutput().getChoices().getFirst().getMessage().getContent().toString())
                .orElse(Strings.EMPTY);
        Matcher matcher = TEXT_PATTERN.matcher(content);
        return matcher.find()
                ? StringUtils.hasText(matcher.group(1)) && !matcher.group(1).equals("-1") ? matcher.group(1) : Strings.EMPTY
                : Strings.EMPTY;
    }

    @Data
    static class OrcResult {
        private String content;
        @SerializedName("prism_wordsInfo")
        private List<WordsInfo> prismWordsInfo;
        private Integer orgHeight;
    }

    @Data
    static class WordsInfo {
        private Integer height;
        private Integer prob;
        private String word;
    }
}