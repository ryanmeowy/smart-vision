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

@Slf4j
@RestController
@RequestMapping("/api/v1/vision")
public class SearchApiController {

    @Resource
    private ImageIngestionService ingestionService;
    @Resource
    private SmartSearchService searchService;

    // 上传图片接口
    @PostMapping("/upload")
    public Result<Void> upload(@RequestParam("file") MultipartFile file) {
        try {
            ingestionService.processAndIndex(file);
        } catch (Exception e) {
            log.error("file upload,  error", e);
            return Result.error("文件上传失败");
        }
        return Result.success();
    }

    // 搜索接口
    @GetMapping("/search")
    public Result<List<SearchResultDTO>> search(@Validated SearchQueryDTO query) {
        return Result.success(searchService.search(query));
    }
}