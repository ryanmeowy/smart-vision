package com.smart.vision.core.controller;

import com.smart.vision.core.manager.HotSearchManager;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.service.search.SmartSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
@RequiredArgsConstructor
public class SearchApiController {

    private final SmartSearchService searchService;
    private final HotSearchManager hotSearchManager;

    @PostMapping("/search")
    public Result<List<SearchResultDTO>> search(@RequestBody SearchQueryDTO query) {
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
}