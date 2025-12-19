package com.smart.vision.core.config;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.teaopenapi.models.Config;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean configuration clazz
 *
 * @author Ryan
 * @since 2025/12/16
 */
@Configuration
@RequiredArgsConstructor
public class BeanConfig {

    private final OssConfig ossConfig;

    /**
     * Register Aliyun OSS client
     * Scope: Singleton
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        return new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret()
        );
    }

    /**
     * Register OCR client
     * Scope: Singleton
     */
    @Bean
    public Client ocrClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(ossConfig.getAccessKeyId())
                .setAccessKeySecret(ossConfig.getAccessKeySecret())
                .setEndpoint("ocr-api.cn-hangzhou.aliyuncs.com");

        return new Client(config);
    }
}