package com.smart.vision.core.controller;

import com.smart.vision.core.component.IdGen;
import com.smart.vision.core.manager.AliyunGenManager;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.GraphTripleDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/api/v1/vision")
@RequiredArgsConstructor
public class TestController {

    private final AliyunGenManager aliyunGenManager;
    private final IdGen idGen;

    @PostMapping("/test")
    public Result<List<GraphTripleDTO>> test(@RequestParam String url) {
        List<GraphTripleDTO> graphTriples = aliyunGenManager.generateGraph(url);
        List<String> strings = aliyunGenManager.generateTags(url);
        return Result.success(graphTriples);
    }

    @GetMapping("/test1")
    public Result<List<Long>> test1() {

        List<Long> ids = Collections.synchronizedList(new ArrayList<>());

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                ids.add(idGen.nextId());
            }
        });

        t1.start();

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                ids.add(idGen.nextId());
            }
        });

        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            log.error("Thread join error", e);
        }
        return Result.success(ids);
    }
}