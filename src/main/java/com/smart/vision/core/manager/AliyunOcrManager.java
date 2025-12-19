package com.smart.vision.core.manager;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralRequest;
import com.aliyun.ocr_api20210707.models.RecognizeGeneralResponse;
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
     * 提取图片文字
     *
     * @param imageUrl 图片的公网 URL (必须是 OSS 签名后的 URL)
     * @return 提取出的纯文本内容
     */
    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 1000))
    public String extractText(String imageUrl) throws Exception {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return "";
        }
        RecognizeGeneralRequest request = new RecognizeGeneralRequest();
        request.setUrl(imageUrl);
        RecognizeGeneralResponse response = client.recognizeGeneral(request);
        return parseOcrResult(response);
    }

    private String parseOcrResult(RecognizeGeneralResponse response) {
        if (response == null || response.getBody() == null || response.getBody().getData() == null) {
            return "";
        }
        return response.getBody().getData();
    }

    @Recover
    public String recover(Exception e, String imageUrl) throws Exception {
        log.error("After retrying 3 times, the OCR service still failed.: {}", imageUrl);
        throw e;
    }
}