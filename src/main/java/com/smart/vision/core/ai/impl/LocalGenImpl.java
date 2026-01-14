
package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ContentGenerationService;
import com.smart.vision.core.grpc.VisionProto;
import com.smart.vision.core.grpc.VisionServiceGrpc;
import com.smart.vision.core.model.enums.PromptEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;

import static com.smart.vision.core.constant.CommonConstant.SSE_TIMEOUT;

@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalGenImpl implements ContentGenerationService {

    @GrpcClient("vision-python-service")
    private VisionServiceGrpc.VisionServiceBlockingStub visionStub;
    private final Executor imageGenTaskExecutor;

    @Override
    public SseEmitter streamGenerateCopy(String imageUrl, String promptType) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        try {
            VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                    .setImageUrl(imageUrl)
                    .setPrompt(PromptEnum.getPromptByType(promptType))
                    .build();
            Iterator<VisionProto.StringResponse> stringResponseIterator = visionStub.generateCaption(request);
            imageGenTaskExecutor.execute(() -> {
                while (stringResponseIterator.hasNext()) {
                    VisionProto.StringResponse response = stringResponseIterator.next();
                    try {
                        emitter.send(response.getContent());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                emitter.complete();
            });
        } catch (Exception e) {
            log.error("gRPC stream generate call failed: {}", e.getMessage());
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public String generateFileName(String imageUrl) {
        try {
            VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                    .setImageUrl(imageUrl)
                    .setPrompt(PromptEnum.NAME_GEN.getPrompt())
                    .build();
            VisionProto.GenFileNameResponse response = visionStub.generateFileName(request);
            return response.getName();
        } catch (Exception e) {
            log.error("gRPC gen name call failed: {}", e.getMessage());
            throw new RuntimeException("Local model service is unavailable.");
        }
    }


    @Override
    public List<String> generateTags(String imageUrl) {
        try {
            VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                    .setImageUrl(imageUrl)
                    .setPrompt(PromptEnum.TAG_GEN.getPrompt())
                    .build();
            VisionProto.GenTagsResponse response = visionStub.generateTags(request);
            return response.getTagList();
        } catch (Exception e) {
            log.error("gRPC gen tags call failed: {}", e.getMessage());
            throw new RuntimeException("Local model service is unavailable.");
        }
    }
}