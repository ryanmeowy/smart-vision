
package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ContentGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Service
@Profile("local-grpc")
public class LocalGenImpl implements ContentGenerationService {

    @Override
    public SseEmitter streamGenerateCopy(String imageUrl, String promptType) {
        log.info("⚡️ [Local] 调用本地 LLM 生成文案");
        SseEmitter emitter = new SseEmitter();
        // 模拟流式输出
        new Thread(() -> {
            try {
                emitter.send("这是本地模型生成的文案...");
                Thread.sleep(500);
                emitter.send("算力有限...");
                Thread.sleep(500);
                emitter.send("仅供演示。");
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    @Override
    public String GenFileName(String imageUrl) {
        return "";
    }


    @Override
    public List<String> generateTags(String imageUrl) {
        return List.of();
    }
}