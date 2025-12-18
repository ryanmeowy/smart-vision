package com.smart.vision.core.controller;

import com.aliyuncs.exceptions.ClientException;
import com.smart.vision.core.annotation.RequireAuth;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.StsTokenDTO;
import com.smart.vision.core.service.search.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * rest api controller for oss functionality;
 *
 * @author Ryan
 * @since 2025/12/17
 */
@RestController
@RequestMapping("/api/v1/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssService ossService;

    @RequireAuth
    @GetMapping("/sts")
    public Result<StsTokenDTO> getStsToken() {
        try {
            return Result.success(ossService.fetchStsToken());
        } catch (ClientException e) {
            return Result.error(e.getMessage());
        }
    }
}