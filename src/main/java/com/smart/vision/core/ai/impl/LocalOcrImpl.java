
package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ImageOcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("local")
public class LocalOcrImpl implements ImageOcrService {

    @Override
    public String extractText(String imageUrl) {
        log.info("⚡️ [Local] 调用本地 gRPC 进行 OCR: {}", imageUrl);
        // TODO: 调用 Python PaddleOCR
        return "本地OCR识别结果测试";
    }
}