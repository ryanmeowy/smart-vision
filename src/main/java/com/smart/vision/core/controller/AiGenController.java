package com.smart.vision.core.controller;

import com.smart.vision.core.manager.AliyunGenManager;
import com.smart.vision.core.manager.OssManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.smart.vision.core.model.enums.PresignedValidityEnum.MEDIUM_TERM_VALIDITY;

/**
 * This controller handles AI generation requests and streams the results back to the client.
 *
 * @author Ryan
 * @since 2025/12/23
 */
@RestController
@RequestMapping("/api/v1/gen")
@RequiredArgsConstructor
public class AiGenController {

    private final AliyunGenManager genManager;
    private final OssManager ossManager;

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter generateStream(@RequestParam String key,
                                     @RequestParam(defaultValue = "xiaohongshu") String style) {
        String tempUrl = ossManager.getPresignedUrl(key, MEDIUM_TERM_VALIDITY.getValidity());
        return genManager.streamGenerateCopy(tempUrl, style);
    }
}