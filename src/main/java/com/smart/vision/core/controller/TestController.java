package com.smart.vision.core.controller;

import com.smart.vision.core.manager.AliyunGenManager;
import com.smart.vision.core.manager.HotSearchManager;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.GraphTripleDTO;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
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


@Slf4j
@RestController
@RequestMapping("/api/v1/vision")
@RequiredArgsConstructor
public class TestController {

    private final AliyunGenManager aliyunGenManager;

    @PostMapping("/test")
    public Result<List<GraphTripleDTO>> test(@RequestParam String url) {
        List<GraphTripleDTO> graphTriples = aliyunGenManager.generateGraph(url);
        List<String> strings = aliyunGenManager.generateTags(url);
        return Result.success(graphTriples);
    }
}