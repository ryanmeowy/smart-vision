package com.smart.vision.core.integration.config.aliyun;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.teaopenapi.models.Config;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Aliyun SDK bean configuration.
 */
@Configuration
@RequiredArgsConstructor
public class AliyunBeanConfig {

    private final AliyunOSSConfig aliyunOssConfig;
    private final AliyunOCRConfig aliyunOcrConfig;

    /**
     * Register Aliyun OSS client.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.capability-provider", name = "object-storage", havingValue = "aliyun")
    public OSS ossClient() {
        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        conf.setProtocol(Protocol.HTTPS);
        return new OSSClientBuilder().build(
                aliyunOssConfig.getEndpoint(),
                aliyunOssConfig.getAccessKeyId(),
                aliyunOssConfig.getAccessKeySecret(),
                conf
        );
    }

    /**
     * Register Aliyun OCR client.
     */
    @Bean
    @Deprecated
    @ConditionalOnProperty(prefix = "app.capability-provider", name = "ocr", havingValue = "aliyun")
    public Client ocrClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(aliyunOcrConfig.getAccessKeyId())
                .setAccessKeySecret(aliyunOcrConfig.getAccessKeySecret())
                .setEndpoint(aliyunOcrConfig.getEndpoint());

        return new Client(config);
    }
}
