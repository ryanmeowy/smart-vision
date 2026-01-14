
package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.grpc.VisionProto;
import com.smart.vision.core.grpc.VisionServiceGrpc;
import com.smart.vision.core.model.enums.PromptEnum;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("local")
public class LocalOcrImpl implements ImageOcrService {

    @GrpcClient("vision-python-service")
    private VisionServiceGrpc.VisionServiceBlockingStub visionStub;


    @Override
    public String extractText(String imageUrl) {
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