package com.smart.vision.core.controller;

import com.smart.vision.core.annotation.RequireAuth;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.BatchProcessDTO;
import com.smart.vision.core.model.dto.BatchTaskStatusDTO;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.smart.vision.core.constant.SearchConstant.DEFAULT_NUM_BATCH_ITEMS;

/**
 * REST API controller for image upload functionality
 * Provides endpoints for uploading images, processing them through OSS, and indexing in Elasticsearch
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/image")
@RequiredArgsConstructor
public class ImageApiController {

    private final ImageIngestionService ingestionService;

    /**
     * Batch processing api (called after frontend direct upload to OSS)
     * Deprecated: use /api/v1/image/batch-tasks for async task flow.
     *
     * @param items list of batch process requests containing OSS keys and file names
     * @return Result containing batch upload statistics
     */
    @Deprecated
    @RequireAuth
    @PostMapping("/batch-process")
    public Result<BatchUploadResultDTO> batchProcess(@RequestBody @Valid List<@Valid BatchProcessDTO> items) {
        if (items.size() > DEFAULT_NUM_BATCH_ITEMS) {
            return Result.error("The number of items processed per request cannot exceed 20");
        }

        BatchUploadResultDTO result = ingestionService.processBatchItems(items);
        return Result.success(result);
    }

    @RequireAuth
    @PostMapping("/batch-tasks")
    public Result<BatchTaskStatusDTO> createBatchTask(@RequestBody @Valid List<@Valid BatchProcessDTO> items) {
        if (items.size() > DEFAULT_NUM_BATCH_ITEMS) {
            return Result.error("The number of items processed per request cannot exceed 20");
        }
        BatchTaskStatusDTO task = ingestionService.submitBatchTask(items);
        return Result.success(task);
    }

    @RequireAuth
    @GetMapping("/batch-tasks/{taskId}")
    public Result<BatchTaskStatusDTO> getBatchTask(@PathVariable String taskId) {
        BatchTaskStatusDTO task = ingestionService.getBatchTaskStatus(taskId);
        return Result.success(task);
    }

    @RequireAuth
    @PostMapping("/batch-tasks/{taskId}/items/{itemId}/retry")
    public Result<BatchTaskStatusDTO> retryBatchTaskItem(@PathVariable String taskId, @PathVariable String itemId) {
        BatchTaskStatusDTO task = ingestionService.retryBatchTaskItem(taskId, itemId);
        return Result.success(task);
    }

    @RequireAuth
    @PostMapping("/batch-tasks/{taskId}/retry-failed")
    public Result<BatchTaskStatusDTO> retryAllFailedBatchTaskItems(@PathVariable String taskId) {
        BatchTaskStatusDTO task = ingestionService.retryAllFailedBatchTaskItems(taskId);
        return Result.success(task);
    }
}
