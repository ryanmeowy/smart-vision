package com.smart.vision.core.controller;

import com.smart.vision.core.manager.HotSearchManager;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.service.search.SmartSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static com.smart.vision.core.constant.CommonConstant.IMAGE_MAX_SIZE;
import static com.smart.vision.core.constant.CommonConstant.MAX_INPUT_LENGTH;

/**
 * rest api controller for vision search functionality;
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vision")
@RequiredArgsConstructor
public class SearchApiController {

    private final SmartSearchService searchService;
    private final HotSearchManager hotSearchManager;

    @PostMapping("/search")
    public Result<List<SearchResultDTO>> search(@RequestBody SearchQueryDTO query) {
        if (null == query || null == query.getKeyword() || query.getKeyword().length() > MAX_INPUT_LENGTH) {
            return Result.success(Collections.emptyList());
        }
        return Result.success(searchService.search(query));
    }

    @GetMapping("/similar")
    public Result<List<SearchResultDTO>> searchSimilar(@RequestParam String id) {
        return Result.success(searchService.searchByVector(id));
    }

    @GetMapping("/hot-words")
    public Result<List<String>> getHotWords() {
        return Result.success(hotSearchManager.getTopHotWords());
    }

    @PostMapping(value = "/search-by-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<SearchResultDTO>> searchByImage(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (file.isEmpty()) {
            return Result.error("Please upload an image");
        }
        if (file.getSize() > IMAGE_MAX_SIZE) {
            return Result.error("The image is too large, please upload an image within 10MB");
        }

        return Result.success(searchService.searchByImage(file, limit));
    }
}