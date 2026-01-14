
package com.smart.vision.core.controller;

import com.aliyuncs.exceptions.ClientException;
import com.smart.vision.core.annotation.RequireAuth;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.service.search.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import static com.smart.vision.core.constant.CommonConstant.TOKEN_KEY;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthApiController {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.admin-secret}")
    private String adminSecret;

    private final OssService ossService;

    private final SecureRandom secureRandom = new SecureRandom();

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return true;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return !MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * Remote refresh upload token interface
     */
    @GetMapping("/refresh-token")
    public Result<String> refreshToken(@RequestHeader("X-Admin-Secret") String secret,
                                       @RequestParam(required = false) String code) {
        if (constantTimeEquals(adminSecret, secret)) {
            return Result.error(403, "Unauthorized access");
        }

        String newToken;
        if (code != null && !code.isBlank()) {
            newToken = code;
        } else {
            // 6-digit secure random token
            int tokenInt = secureRandom.nextInt(900_000) + 100_000;
            newToken = String.valueOf(tokenInt);
        }
        try {
            redisTemplate.opsForValue().set(TOKEN_KEY, newToken, 12, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to store token in redis", e);
            return Result.error(500, "Internal error");
        }
        return Result.success(newToken);
    }

    @GetMapping("/clean-token")
    public Result<Void> cleanToken(@RequestHeader("X-Admin-Secret") String secret) {
        if (constantTimeEquals(adminSecret, secret)) {
            return Result.error(403, "Unauthorized access");
        }
        try {
            redisTemplate.delete(TOKEN_KEY);
        } catch (Exception e) {
            log.warn("Failed to delete token in redis", e);
            return Result.error(500, "Internal error");
        }
        return Result.success();
    }

    @RequireAuth
    @GetMapping("/sts")
    public Result<String> getStsToken() {
        try {
            return Result.success(ossService.fetchStsToken());
        } catch (ClientException e) {
            log.error("Failed to fetch STS token", e);
            return Result.error(500, "Failed to fetch STS token");
        } catch (Exception e) {
            log.error("Unexpected error fetching STS token", e);
            return Result.error(500, "Internal error");
        }
    }
}