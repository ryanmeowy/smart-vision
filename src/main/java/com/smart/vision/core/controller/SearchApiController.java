package com.smart.vision.core.controller;

import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.service.search.SmartSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/search")
    public Result<List<SearchResultDTO>> search(@Validated SearchQueryDTO query) {
        return Result.success(searchService.search(query));
    }

    @GetMapping("/similar")
    public Result<List<SearchResultDTO>> searchSimilar(@RequestParam String id) {
        return Result.success(searchService.searchByVector(id));
    }
}