package com.smart.vision.core.manager;

import com.aliyun.oss.OSS;
import com.smart.vision.core.config.OSSConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Date;

/**
 * Aliyun OSS service
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssManager {

    private final OSS ossClient;
    @Qualifier("OSSConfig")
    private final OSSConfig ossConfig;

    /**
     * Generate signed temporary access URL
     *
     * @param path         File path in OSS
     * @param validityTime Validity period, unit: ms
     * @return Signed URL
     */
    public String getPresignedUrl(String path, Long validityTime) {
        Date expiration = new Date(System.currentTimeMillis() + validityTime);
        URL url = ossClient.generatePresignedUrl(ossConfig.getBucketName(), path, expiration);
        return url.toString();
    }
}