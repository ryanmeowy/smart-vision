package com.smart.vision.core.service.ingestion.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.vision.core.ai.ContentGenerationService;
import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.ai.MultiModalEmbeddingService;
import com.smart.vision.core.component.EsBatchTemplate;
import com.smart.vision.core.component.IdGen;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.BulkSaveResult;
import com.smart.vision.core.model.dto.BatchProcessDTO;
import com.smart.vision.core.model.dto.BatchTaskStatusDTO;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import com.smart.vision.core.model.dto.GraphTripleDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.smart.vision.core.constant.AliyunConstant.X_OSS_PROCESS_EMBEDDING;
import static com.smart.vision.core.constant.CacheConstant.HASH_INDEX_CACHE_PREFIX;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_IMAGE_NAME;
import static com.smart.vision.core.constant.CommonConstant.PROFILE_KEY_NAME;
import static com.smart.vision.core.model.enums.PresignedValidityEnum.SHORT_TERM_VALIDITY;

/**
 * image ingestion service cloud implementation
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageIngestionServiceImpl implements ImageIngestionService {
    private static final String HASH_STATUS_PROCESSING = "PROCESSING";
    private static final String HASH_STATUS_SUCCESS = "SUCCESS";
    private static final String HASH_STATUS_FAILED = "FAILED";
    private static final long HASH_PROCESSING_TTL_MINUTES = 30L;
    private static final long HASH_SUCCESS_TTL_DAYS = 30L;
    private static final long HASH_FAILED_TTL_MINUTES = 10L;

    private static final String TASK_STATUS_PENDING = "PENDING";
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String TASK_STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    private static final String TASK_STATUS_FAILED = "FAILED";

    private static final String TASK_ITEM_STATUS_PENDING = "PENDING";
    private static final String TASK_ITEM_STATUS_RUNNING = "RUNNING";
    private static final String TASK_ITEM_STATUS_SUCCESS = "SUCCESS";
    private static final String TASK_ITEM_STATUS_FAILED = "FAILED";

    private static final String BATCH_TASK_CACHE_PREFIX = "img:batch:task:";
    private static final String BATCH_TASK_LOCK_PREFIX = "img:batch:task:lock:";
    private static final long BATCH_TASK_TTL_HOURS = 24L;
    private static final long BATCH_TASK_LOCK_TTL_SECONDS = 600L;

    private final EsBatchTemplate esBatchTemplate;
    @Qualifier("embedTaskExecutor")
    private final Executor embedTaskExecutor;
    @Qualifier("ingestionTaskExecutor")
    private final Executor ingestionTaskExecutor;
    private final OssManager ossManager;
    private final MultiModalEmbeddingService embeddingService;
    private final ImageOcrService imageOcrService;
    private final ContentGenerationService contentGenerationService;
    private final StringRedisTemplate redisTemplate;
    private final IdGen idGen;
    private final ObjectMapper objectMapper;

    @Override
    public BatchUploadResultDTO processBatchItems(List<BatchProcessDTO> items) {
        Set<String> seenHashes = new HashSet<>();
        List<BatchProcessDTO> uniqueItems = items.stream()
                .filter(x -> Objects.nonNull(x.getFileHash()))
                .filter(x -> seenHashes.add(x.getFileHash()))
                .toList();

        List<ImageDocument> successDocs = Collections.synchronizedList(new ArrayList<>());
        List<BatchUploadResultDTO.BatchFailureItem> failures = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = uniqueItems.stream()
                .map(item -> CompletableFuture.runAsync(() -> {
                    try {
                        ImageDocument doc = processSingleItem(item);
                        successDocs.add(doc);
                    } catch (Exception e) {
                        log.warn("image processing failed [{}]: {}", item.getFileName(), e.getMessage());
                        failures.add(BatchUploadResultDTO.BatchFailureItem.builder()
                                .objectKey(item.getKey())
                                .filename(item.getFileName())
                                .errorMessage(e.getMessage())
                                .build());
                    }
                }, embedTaskExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int savedCount = 0;
        if (!successDocs.isEmpty()) {
            try {
                BulkSaveResult bulkResult = esBatchTemplate.bulkSave(successDocs);
                savedCount = bulkResult.getSuccessCount();

                Set<String> failedIdSet = bulkResult.getFailedIds();
                for (ImageDocument doc : successDocs) {
                    if (failedIdSet.contains(String.valueOf(doc.getId()))) {
                        markHashStatus(doc.getFileHash(), HASH_STATUS_FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
                        failures.add(BatchUploadResultDTO.BatchFailureItem.builder()
                                .objectKey(doc.getImagePath())
                                .filename(doc.getRawFilename())
                                .errorMessage("Database writes failed")
                                .build());
                    } else {
                        markHashStatus(doc.getFileHash(), HASH_STATUS_SUCCESS, HASH_SUCCESS_TTL_DAYS, TimeUnit.DAYS);
                    }
                }
            } catch (Exception e) {
                log.error("ES writes failed", e);
                for (ImageDocument doc : successDocs) {
                    markHashStatus(doc.getFileHash(), HASH_STATUS_FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
                    failures.add(BatchUploadResultDTO.BatchFailureItem.builder()
                            .objectKey(doc.getImagePath())
                            .filename(doc.getRawFilename())
                            .errorMessage("Database writes failed")
                            .build());
                }
            }
        }

        return BatchUploadResultDTO.builder()
                .total(items.size())
                .successCount(savedCount)
                .failureCount(failures.size())
                .failures(failures)
                .build();
    }

    @Override
    public BatchTaskStatusDTO submitBatchTask(List<BatchProcessDTO> items) {
        BatchTaskStatusDTO task = new BatchTaskStatusDTO();
        long now = System.currentTimeMillis();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStatus(TASK_STATUS_PENDING);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setCompletedAt(null);

        List<BatchTaskStatusDTO.ItemStatus> itemStatuses = new ArrayList<>();
        for (BatchProcessDTO item : items) {
            BatchTaskStatusDTO.ItemStatus itemStatus = new BatchTaskStatusDTO.ItemStatus();
            itemStatus.setItemId(UUID.randomUUID().toString());
            itemStatus.setKey(item.getKey());
            itemStatus.setFileName(item.getFileName());
            itemStatus.setFileHash(item.getFileHash());
            itemStatus.setStatus(TASK_ITEM_STATUS_PENDING);
            itemStatus.setErrorMessage(null);
            itemStatus.setRetryCount(0);
            itemStatus.setUpdatedAt(now);
            itemStatuses.add(itemStatus);
        }

        task.setItems(itemStatuses);
        refreshTaskSummary(task);
        saveTask(task);

        ingestionTaskExecutor.execute(() -> processTask(task.getTaskId()));
        return task;
    }

    @Override
    public BatchTaskStatusDTO getBatchTaskStatus(String taskId) {
        BatchTaskStatusDTO task = loadTask(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }
        return task;
    }

    @Override
    public BatchTaskStatusDTO retryBatchTaskItem(String taskId, String itemId) {
        BatchTaskStatusDTO task = loadTask(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }
        if (TASK_STATUS_RUNNING.equals(task.getStatus())) {
            throw new RuntimeException("Task is running, retry after current round completes.");
        }

        BatchTaskStatusDTO.ItemStatus target = task.getItems().stream()
                .filter(item -> itemId.equals(item.getItemId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Task item not found"));

        if (!TASK_ITEM_STATUS_FAILED.equals(target.getStatus())) {
            throw new RuntimeException("Only FAILED item can be retried");
        }

        long now = System.currentTimeMillis();
        target.setStatus(TASK_ITEM_STATUS_PENDING);
        target.setErrorMessage(null);
        target.setRetryCount(target.getRetryCount() + 1);
        target.setUpdatedAt(now);

        task.setUpdatedAt(now);
        task.setCompletedAt(null);
        refreshTaskSummary(task);
        saveTask(task);

        ingestionTaskExecutor.execute(() -> processTask(task.getTaskId()));
        return task;
    }

    @Override
    public BatchTaskStatusDTO retryAllFailedBatchTaskItems(String taskId) {
        BatchTaskStatusDTO task = loadTask(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }
        if (TASK_STATUS_RUNNING.equals(task.getStatus())) {
            throw new RuntimeException("Task is running, retry after current round completes.");
        }

        long now = System.currentTimeMillis();
        int changed = 0;
        for (BatchTaskStatusDTO.ItemStatus item : task.getItems()) {
            if (!TASK_ITEM_STATUS_FAILED.equals(item.getStatus())) {
                continue;
            }
            item.setStatus(TASK_ITEM_STATUS_PENDING);
            item.setErrorMessage(null);
            item.setRetryCount(item.getRetryCount() + 1);
            item.setUpdatedAt(now);
            changed++;
        }

        if (changed == 0) {
            throw new RuntimeException("No FAILED items to retry");
        }

        task.setUpdatedAt(now);
        task.setCompletedAt(null);
        refreshTaskSummary(task);
        saveTask(task);

        ingestionTaskExecutor.execute(() -> processTask(task.getTaskId()));
        return task;
    }

    /**
     * Processes a single image item through the complete pipeline
     *
     * @param item the batch process request containing OSS key and file metadata
     */
    protected ImageDocument processSingleItem(BatchProcessDTO item) {
        String cacheKey = String.format("%s%s:%s", HASH_INDEX_CACHE_PREFIX, System.getenv(PROFILE_KEY_NAME), item.getFileHash());
        if (!acquireHashProcessingLock(cacheKey, item.getFileHash(), item.getFileName())) {
            throw new RuntimeException("Image is processing, retry later.");
        }
        try {
            String tempUrl = ossManager.getAiPresignedUrl(item.getKey(), SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
            List<Float> vector = embeddingService.embedImage(tempUrl);
            String ocrText = imageOcrService.extractText(tempUrl);
            List<String> tags = contentGenerationService.generateTags(tempUrl);
            List<GraphTripleDTO> graphTriples = contentGenerationService.generateGraph(tempUrl);

            ImageDocument doc = new ImageDocument();
            doc.setId(idGen.nextId());
            doc.setImagePath(item.getKey());
            doc.setRawFilename(item.getFileName());
            doc.setFileName(genFileName(tempUrl));
            doc.setImageEmbedding(vector);
            doc.setOcrContent(ocrText);
            doc.setCreateTime(System.currentTimeMillis());
            doc.setTags(tags);
            doc.setFileHash(item.getFileHash());
            doc.setRelations(graphTriples);
            return doc;
        } catch (Exception e) {
            markHashStatus(item.getFileHash(), HASH_STATUS_FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
            throw e;
        }
    }

    protected String genFileName(String imageUrl) {
        String name = StringUtils.hasText(imageUrl) ? contentGenerationService.generateFileName(imageUrl) : DEFAULT_IMAGE_NAME;
        return String.format("%s-%s", name, System.currentTimeMillis());
    }

    private boolean acquireHashProcessingLock(String cacheKey, String fileHash, String fileName) {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(cacheKey, HASH_STATUS_PROCESSING, HASH_PROCESSING_TTL_MINUTES, TimeUnit.MINUTES);
        if (Boolean.TRUE.equals(locked)) {
            return true;
        }

        String currentStatus = redisTemplate.opsForValue().get(cacheKey);
        if (HASH_STATUS_SUCCESS.equals(currentStatus)) {
            log.info("Duplicate image hash [{}] [{}]", fileHash, fileName);
            throw new RuntimeException("Duplicate image, skipped.");
        }
        if (HASH_STATUS_PROCESSING.equals(currentStatus)) {
            log.info("Image is processing [{}] [{}]", fileHash, fileName);
            return false;
        }
        if (HASH_STATUS_FAILED.equals(currentStatus)) {
            redisTemplate.delete(cacheKey);
            Boolean retryLocked = redisTemplate.opsForValue().setIfAbsent(cacheKey, HASH_STATUS_PROCESSING, HASH_PROCESSING_TTL_MINUTES, TimeUnit.MINUTES);
            if (Boolean.TRUE.equals(retryLocked)) {
                log.info("Retry processing for failed image hash [{}] [{}]", fileHash, fileName);
                return true;
            }
        }
        return false;
    }

    private void markHashStatus(String fileHash, String status, long ttl, TimeUnit unit) {
        String cacheKey = String.format("%s%s:%s", HASH_INDEX_CACHE_PREFIX, System.getenv(PROFILE_KEY_NAME), fileHash);
        redisTemplate.opsForValue().set(cacheKey, status, ttl, unit);
    }

    private void processTask(String taskId) {
        String lockValue = UUID.randomUUID().toString();
        if (!acquireTaskLock(taskId, lockValue)) {
            return;
        }

        try {
            BatchTaskStatusDTO task = loadTask(taskId);
            if (task == null) {
                return;
            }

            boolean hasPending = task.getItems().stream().anyMatch(x -> TASK_ITEM_STATUS_PENDING.equals(x.getStatus()));
            if (!hasPending) {
                refreshTaskSummary(task);
                saveTask(task);
                return;
            }

            List<BatchTaskStatusDTO.ItemStatus> pendingItems = task.getItems().stream()
                    .filter(x -> TASK_ITEM_STATUS_PENDING.equals(x.getStatus()))
                    .toList();
            if (pendingItems.isEmpty()) {
                refreshTaskSummary(task);
                saveTask(task);
                return;
            }

            List<CompletableFuture<Void>> futures = pendingItems.stream()
                    .map(itemStatus -> CompletableFuture.runAsync(() -> processTaskItem(task, taskId, itemStatus), embedTaskExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            synchronized (task) {
                refreshTaskSummary(task);
                saveTask(task);
            }
        } finally {
            releaseTaskLock(taskId, lockValue);
        }
    }

    private void processTaskItem(BatchTaskStatusDTO task, String taskId, BatchTaskStatusDTO.ItemStatus itemStatus) {
        synchronized (task) {
            itemStatus.setStatus(TASK_ITEM_STATUS_RUNNING);
            itemStatus.setErrorMessage(null);
            itemStatus.setUpdatedAt(System.currentTimeMillis());
            refreshTaskSummary(task);
            saveTask(task);
        }

        try {
            ImageDocument doc = processSingleItem(toBatchProcessDTO(itemStatus));
            boolean saved = saveSingleDoc(doc);
            synchronized (task) {
                if (saved) {
                    itemStatus.setStatus(TASK_ITEM_STATUS_SUCCESS);
                    itemStatus.setErrorMessage(null);
                } else {
                    itemStatus.setStatus(TASK_ITEM_STATUS_FAILED);
                    itemStatus.setErrorMessage("Database writes failed");
                }
                itemStatus.setUpdatedAt(System.currentTimeMillis());
                refreshTaskSummary(task);
                saveTask(task);
            }
        } catch (Exception e) {
            log.warn("task [{}] item [{}] failed: {}", taskId, itemStatus.getFileName(), e.getMessage());
            synchronized (task) {
                itemStatus.setStatus(TASK_ITEM_STATUS_FAILED);
                itemStatus.setErrorMessage(e.getMessage());
                itemStatus.setUpdatedAt(System.currentTimeMillis());
                refreshTaskSummary(task);
                saveTask(task);
            }
        }
    }

    private boolean saveSingleDoc(ImageDocument doc) {
        try {
            BulkSaveResult bulkSaveResult = esBatchTemplate.bulkSave(List.of(doc));
            boolean failed = bulkSaveResult.getFailedIds() != null
                    && bulkSaveResult.getFailedIds().contains(String.valueOf(doc.getId()));
            if (failed) {
                markHashStatus(doc.getFileHash(), HASH_STATUS_FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
                return false;
            }

            markHashStatus(doc.getFileHash(), HASH_STATUS_SUCCESS, HASH_SUCCESS_TTL_DAYS, TimeUnit.DAYS);
            return true;
        } catch (Exception e) {
            log.error("ES writes failed in async task", e);
            markHashStatus(doc.getFileHash(), HASH_STATUS_FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
            return false;
        }
    }

    private BatchProcessDTO toBatchProcessDTO(BatchTaskStatusDTO.ItemStatus itemStatus) {
        BatchProcessDTO item = new BatchProcessDTO();
        item.setKey(itemStatus.getKey());
        item.setFileName(itemStatus.getFileName());
        item.setFileHash(itemStatus.getFileHash());
        return item;
    }

    private void refreshTaskSummary(BatchTaskStatusDTO task) {
        int total = task.getItems().size();
        int success = 0;
        int failure = 0;
        int running = 0;
        int pending = 0;

        for (BatchTaskStatusDTO.ItemStatus item : task.getItems()) {
            if (TASK_ITEM_STATUS_SUCCESS.equals(item.getStatus())) {
                success++;
            } else if (TASK_ITEM_STATUS_FAILED.equals(item.getStatus())) {
                failure++;
            } else if (TASK_ITEM_STATUS_RUNNING.equals(item.getStatus())) {
                running++;
            } else if (TASK_ITEM_STATUS_PENDING.equals(item.getStatus())) {
                pending++;
            }
        }

        task.setTotal(total);
        task.setSuccessCount(success);
        task.setFailureCount(failure);
        task.setRunningCount(running);
        task.setPendingCount(pending);
        task.setUpdatedAt(System.currentTimeMillis());

        if (pending == total && running == 0 && success == 0 && failure == 0) {
            task.setStatus(TASK_STATUS_PENDING);
            task.setCompletedAt(null);
            return;
        }

        if (pending == 0 && running == 0) {
            if (success == total) {
                task.setStatus(TASK_STATUS_SUCCESS);
            } else if (failure == total) {
                task.setStatus(TASK_STATUS_FAILED);
            } else {
                task.setStatus(TASK_STATUS_PARTIAL_FAILED);
            }
            task.setCompletedAt(System.currentTimeMillis());
            return;
        }

        task.setStatus(TASK_STATUS_RUNNING);
        task.setCompletedAt(null);
    }

    private boolean acquireTaskLock(String taskId, String lockValue) {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                BATCH_TASK_LOCK_PREFIX + taskId,
                lockValue,
                BATCH_TASK_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(locked);
    }

    private void releaseTaskLock(String taskId, String lockValue) {
        String lockKey = BATCH_TASK_LOCK_PREFIX + taskId;
        String current = redisTemplate.opsForValue().get(lockKey);
        if (Objects.equals(current, lockValue)) {
            redisTemplate.delete(lockKey);
        }
    }

    private void saveTask(BatchTaskStatusDTO task) {
        redisTemplate.opsForValue().set(
                BATCH_TASK_CACHE_PREFIX + task.getTaskId(),
                serializeTask(task),
                BATCH_TASK_TTL_HOURS,
                TimeUnit.HOURS
        );
    }

    private BatchTaskStatusDTO loadTask(String taskId) {
        String raw = redisTemplate.opsForValue().get(BATCH_TASK_CACHE_PREFIX + taskId);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, BatchTaskStatusDTO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse task payload", e);
        }
    }

    private String serializeTask(BatchTaskStatusDTO task) {
        try {
            return objectMapper.writeValueAsString(task);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize task payload", e);
        }
    }
}
