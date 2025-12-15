package com.smart.vision.core.controller;

import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import com.smart.vision.core.service.search.SmartSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * rest api controller for vision search functionality;
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vision")
public class SearchApiController {

    @Resource
    private ImageIngestionService ingestionService;
    @Resource
    private SmartSearchService searchService;

    @PostMapping("/upload")
    public Result<Void> upload(@RequestParam("file") MultipartFile file) {
        try {
            ingestionService.processAndIndex(file);
        } catch (Exception e) {
            log.error("file upload error", e);
            return Result.error("文件上传失败");
        }
        return Result.success();
    }

    @GetMapping("/search")
    public Result<List<SearchResultDTO>> search(@Validated SearchQueryDTO query) {
        return Result.success(searchService.search(query));
    }
}