
package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.grpc.VisionProto;
import com.smart.vision.core.grpc.VisionServiceGrpc;
import com.smart.vision.core.model.enums.PromptEnum;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.logging.log4j.util.Strings;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@Profile("local")
public class LocalOcrImpl implements ImageOcrService {

    @SuppressWarnings("unused")
    @GrpcClient("vision-python-service")
    private VisionServiceGrpc.VisionServiceBlockingStub visionStub;


    @Override
    public String extractText(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return Strings.EMPTY;
        try {
            VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                    .setImageUrl(imageUrl)
                    .setPrompt(PromptEnum.OCR.getPrompt())
                    .build();
            VisionProto.OcrResponse ocrResponse = visionStub.extractText(request);
            return ocrResponse.getFullText();
        } catch (Exception e) {
            log.error("gRPC ocr call failed: {}", e.getMessage());
            throw new RuntimeException("Local model service is unavailable.");
        }
    }
}