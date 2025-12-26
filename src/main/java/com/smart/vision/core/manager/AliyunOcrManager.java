package com.smart.vision.core.manager;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralRequest;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

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

    private String parseOcrResult(RecognizeGeneralResponse response) {
        if (response == null || response.getBody() == null || response.getBody().getData() == null) {
            return null;
        }
        JsonObject jsonObject = JsonParser.parseString(response.getBody().getData()).getAsJsonObject();
        String content = jsonObject.get("content").toString();
        if (content == null || content.isEmpty() || content.equals("\"\"")) {
            return null;
        }
        return content;
    }

    @Recover
    public String recover(Exception e, String imageUrl) throws Exception {
        log.error("After retrying 3 times, the OCR service still failed.: {}", imageUrl);
        throw e;
    }
}