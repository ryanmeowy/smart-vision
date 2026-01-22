package com.smart.vision.core.ai;

import com.smart.vision.core.grpc.VisionProto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AIGC Generation Service Interface
 */
public interface ContentGenerationService {

    /**
     * Generate copywriting in a streaming manner (SSE)
     * @param imageUrl Image URL
     * @param promptType Prompt type/style
     * @return SSE Emitter
     */
    SseEmitter streamGenerateCopy(String imageUrl, String promptType);

    /**
     * Generate a unique file name for the image
     * @param imageUrl Image URL
     * @return Unique file name
     */
    String generateFileName(String imageUrl);

    /**
     * Generate tags for the image
     * @param imageUrl Image URL
     * @return List of tags
     */
    List<String> generateTags(String imageUrl);

    /**
     * Generate graph for the image
     * @param imageUrl Image URL
     * @return List of graph triples
     */
    List<VisionProto.GraphTriple> generateGraph(String imageUrl);
}