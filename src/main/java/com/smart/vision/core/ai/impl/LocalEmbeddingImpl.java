package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.MultiModalEmbeddingService;
import com.smart.vision.core.grpc.VisionProto;
import com.smart.vision.core.grpc.VisionServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Profile("local")
public class LocalEmbeddingImpl implements MultiModalEmbeddingService {

    @GrpcClient("vision-python-service")
    private VisionServiceGrpc.VisionServiceBlockingStub visionStub;

    /**
     * Get multimodal vector (image)
     *
     * @param imageUrl Image URL (optional)
     * @return 1024-dimensional vector
     */
    @Override
    public List<Float> embedImage(String imageUrl) {
        try {
            VisionProto.ImageRequest request = VisionProto.ImageRequest.newBuilder().setUrl(imageUrl).build();
            VisionProto.EmbeddingResponse embeddingResponse = visionStub.embedImage(request);
            return embeddingResponse.getVectorList();
        } catch (Exception e) {
            log.error("gRPC image embedding call failed", e);
            throw new RuntimeException("Local model service is unavailable.");
        }
    }

    /**
     * Get multimodal vector (text)
     *
     * @param text Text (optional)
     * @return 1024-dimensional vector
     */
    @Override
    public List<Float> embedText(String text) {
        try {
            VisionProto.TextRequest request = VisionProto.TextRequest.newBuilder().setText(text).build();
            VisionProto.EmbeddingResponse embeddingResponse = visionStub.embedText(request);
            return embeddingResponse.getVectorList();
        } catch (Exception e) {
            log.error("gRPC text embedding call failed: {}", e.getMessage());
            throw new RuntimeException("Local model service is unavailable.");
        }
    }
}