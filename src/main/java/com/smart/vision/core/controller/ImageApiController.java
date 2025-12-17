package com.smart.vision.core.controller;

import com.smart.vision.core.model.Result;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * rest api controller for image upload functionality;
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vision")
@RequiredArgsConstructor
public class ImageApiController {

    private final ImageIngestionService ingestionService;

    @PostMapping("/upload")
    public Result<Void> upload(@RequestParam("file") MultipartFile file) {
        try {
            ingestionService.processAndIndex(file);
        } catch (Exception e) {
            log.error("file upload error", e);
            return Result.error("File upload failed");
        }
        return Result.success();
    }

    @PostMapping("/batch-process")
    public Result<String> batchProcessUrl(@RequestBody List<String> imageUrls) {
        return Result.success(ingestionService.processUrls(imageUrls));
    }
}
