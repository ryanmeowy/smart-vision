package com.smart.vision.core.controller;

import com.aliyuncs.exceptions.ClientException;
import com.smart.vision.core.annotation.RequireAuth;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.StsTokenDTO;
import com.smart.vision.core.service.search.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

import static com.smart.vision.core.constant.CommonConstant.TOKEN_KEY;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.admin-secret}")
    private String adminSecret;

    private final OssService ossService;

    /**
     * Remote refresh upload token interface
     */
    @GetMapping("/refresh-token")
    public Result<String> refreshToken(@RequestHeader("X-Admin-Secret") String secret,
                                       @RequestParam(required = false) String code) {
        if (!adminSecret.equals(secret)) {
            // prevent timing attack
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ignored) {
            }
            return Result.error(403, "Unauthorized access: Wrong key");
        }

        String newToken;
        if (code != null && !code.isBlank()) {
            newToken = code;
        } else {
            newToken = String.valueOf((int) ((Math.random() * 9 + 1) * 100_000));
        }
        redisTemplate.opsForValue().set(TOKEN_KEY, newToken, 12, TimeUnit.HOURS);
        return Result.success(newToken);
    }

    @GetMapping("/clean-token")
    public Result<Void> cleanToken(@RequestHeader("X-Admin-Secret") String secret) {
        if (!adminSecret.equals(secret)) {
            // prevent timing attack
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ignored) {
            }
            return Result.error(403, "Unauthorized access: Wrong key");
        }
        redisTemplate.delete(TOKEN_KEY);
        return Result.success();
    }

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