package com.smart.vision.core;

import com.smart.vision.core.integration.config.aliyun.AliyunOCRConfig;
import com.smart.vision.core.integration.config.aliyun.AliyunOSSConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableRetry
@SpringBootApplication
@EnableConfigurationProperties({AliyunOSSConfig.class, AliyunOCRConfig.class})
public class SmartVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartVisionApplication.class, args);
    }

}
