package com.smart.vision.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool configuration clazz
 *
 * @author Ryan
 * @since 2025/12/16
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * Create image upload task thread pool
     * Used for handling time-consuming operations like image upload, OCR recognition, and vector generation
     *
     * @return Image upload task executor
     */
    @Bean("imageUploadTaskExecutor")
    public Executor imageUploadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("img-upload-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Create embedding calculation task thread pool
     * Used for handling AI-related tasks like text vectorization and similarity calculation
     *
     * @return Embedding calculation task executor
     */
    @Bean("embedTaskExecutor")
    public Executor embedTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("embed-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}