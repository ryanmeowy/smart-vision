package com.smart.vision.core.manager;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralRequest;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralResponse;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.CommonConstant.DIGIT_REGEX;
import static com.smart.vision.core.constant.CommonConstant.PUNCTUATION_REGEX;
import static com.smart.vision.core.constant.CommonConstant.SINGLE_LETTER_REGEX;
import static com.smart.vision.core.constant.CommonConstant.URL_REGEX;
import static com.smart.vision.core.constant.CommonConstant.WHITE_SPACE_REGEX;

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

    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);
    private static final Pattern SINGLE_LETTER_PATTERN = Pattern.compile(SINGLE_LETTER_REGEX);
    private static final Pattern DIGIT_PATTERN = Pattern.compile(DIGIT_REGEX);

    /**
     * Extract text from image
     *
     * @param imageUrl Public URL of the image (must be an OSS signed URL)
     * @return Extracted plain text content
     */
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