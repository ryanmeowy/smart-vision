package com.smart.vision.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OCR configuration clazz
 *
 * @author Ryan
 * @since 2025/12/22
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.ocr")
public class OCRConfig {
    /**
     * Access Key ID
     */
    private String accessKeyId;
    /**
     * Access Key Secret
     */
    private String accessKeySecret;
    /**
     * Endpoint
     */
    private String endpoint;
}
