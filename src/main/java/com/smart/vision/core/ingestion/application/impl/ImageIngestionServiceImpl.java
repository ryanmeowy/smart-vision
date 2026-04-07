package com.smart.vision.core.ingestion.application.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.vision.core.ingestion.application.ImageIngestionService;
import com.smart.vision.core.ingestion.application.assembler.BatchTaskAssembler;
import com.smart.vision.core.ingestion.domain.model.BatchTask;
import com.smart.vision.core.ingestion.domain.model.BatchTaskItem;
import com.smart.vision.core.ingestion.domain.model.BatchTaskItemStatus;
import com.smart.vision.core.ingestion.domain.model.BulkSaveResult;
import com.smart.vision.core.ingestion.domain.model.ImageHashAcquireOutcome;
import com.smart.vision.core.ingestion.domain.model.ImageHashAcquireOutcomeType;
import com.smart.vision.core.ingestion.domain.model.ImageHashStatePolicy;
import com.smart.vision.core.ingestion.domain.model.ImageHashStatus;
import com.smart.vision.core.ingestion.domain.port.ImageHashStateRepository;
import com.smart.vision.core.ingestion.infrastructure.id.IdGen;
import com.smart.vision.core.ingestion.infrastructure.persistence.es.EsBatchTemplate;
import com.smart.vision.core.ingestion.interfaces.rest.dto.BatchProcessDTO;
import com.smart.vision.core.ingestion.interfaces.rest.dto.BatchTaskStatusDTO;
import com.smart.vision.core.ingestion.interfaces.rest.dto.BatchUploadResultDTO;
import com.smart.vision.core.integration.ai.port.ContentGenerationService;
import com.smart.vision.core.integration.ai.port.ImageOcrService;
import com.smart.vision.core.integration.ai.port.MultiModalEmbeddingService;
import com.smart.vision.core.integration.oss.OssManager;
import com.smart.vision.core.search.domain.model.GraphTriple;
import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
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

import static com.smart.vision.core.common.constant.AliyunConstant.X_OSS_PROCESS_EMBEDDING;
import static com.smart.vision.core.common.constant.CommonConstant.DEFAULT_IMAGE_NAME;
import static com.smart.vision.core.integration.oss.domain.model.PresignedValidityEnum.SHORT_TERM_VALIDITY;

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
    private static final long HASH_PROCESSING_TTL_MINUTES = 30L;
    private static final long HASH_SUCCESS_TTL_DAYS = 30L;
    private static final long HASH_FAILED_TTL_MINUTES = 10L;

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
    private final ImageHashStateRepository imageHashStateRepository;
    private final BatchTaskAssembler batchTaskAssembler;
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
                        markHashStatus(doc.getFileHash(), ImageHashStatus.FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
                        failures.add(BatchUploadResultDTO.BatchFailureItem.builder()
                                .objectKey(doc.getImagePath())
                                .filename(doc.getRawFilename())
                                .errorMessage("Database writes failed")
                                .build());
                    } else {
                        markHashStatus(doc.getFileHash(), ImageHashStatus.SUCCESS, HASH_SUCCESS_TTL_DAYS, TimeUnit.DAYS);
                    }
                }
            } catch (Exception e) {
                log.error("ES writes failed", e);
                for (ImageDocument doc : successDocs) {
                    markHashStatus(doc.getFileHash(), ImageHashStatus.FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
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
        long now = System.currentTimeMillis();
        List<BatchTaskItem> taskItems = new ArrayList<>();
        for (BatchProcessDTO item : items) {
            BatchTaskItem taskItem = new BatchTaskItem();
            taskItem.setItemId(UUID.randomUUID().toString());
            taskItem.setKey(item.getKey());
            taskItem.setFileName(item.getFileName());
            taskItem.setFileHash(item.getFileHash());
            taskItem.setStatus(BatchTaskItemStatus.PENDING);
            taskItem.setErrorMessage(null);
            taskItem.setRetryCount(0);
            taskItem.setUpdatedAt(now);
            taskItems.add(taskItem);
        }

        BatchTask task = BatchTask.createPending(UUID.randomUUID().toString(), taskItems, now);
        saveTask(task);
        ingestionTaskExecutor.execute(() -> processTask(task.getTaskId()));
        return batchTaskAssembler.toTaskDto(task);
    }

    @Override
    public BatchTaskStatusDTO getBatchTaskStatus(String taskId) {
        BatchTask task = loadTask(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }
        return batchTaskAssembler.toTaskDto(task);
    }

    @Override
    public BatchTaskStatusDTO retryBatchTaskItem(String taskId, String itemId) {
        BatchTask task = loadTask(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }

        task.retryItem(itemId, System.currentTimeMillis());
        saveTask(task);

        ingestionTaskExecutor.execute(() -> processTask(task.getTaskId()));
        return batchTaskAssembler.toTaskDto(task);
    }

    @Override
    public BatchTaskStatusDTO retryAllFailedBatchTaskItems(String taskId) {
        BatchTask task = loadTask(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }

        task.retryAllFailed(System.currentTimeMillis());
        saveTask(task);

        ingestionTaskExecutor.execute(() -> processTask(task.getTaskId()));
        return batchTaskAssembler.toTaskDto(task);
    }

    /**
     * Processes a single image item through the complete pipeline
     *
     * @param item the batch process request containing OSS key and file metadata
     */
    protected ImageDocument processSingleItem(BatchProcessDTO item) {
        if (!acquireHashProcessingLock(item.getFileHash(), item.getFileName())) {
            throw new RuntimeException("Image is processing, retry later.");
        }
        try {
            String tempUrl = ossManager.getAiPresignedUrl(item.getKey(), SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
            List<Float> vector = embeddingService.embedImage(tempUrl);
            String ocrText = imageOcrService.extractText(tempUrl);
            List<String> tags = contentGenerationService.generateTags(tempUrl);
            List<GraphTriple> graphTriples = contentGenerationService.generateGraph(tempUrl);

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
            doc.setRelations(toDomainTriples(graphTriples));
            return doc;
        } catch (Exception e) {
            markHashStatus(item.getFileHash(), ImageHashStatus.FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
            throw e;
        }
    }

    protected String genFileName(String imageUrl) {
        String name = StringUtils.hasText(imageUrl) ? contentGenerationService.generateFileName(imageUrl) : DEFAULT_IMAGE_NAME;
        return String.format("%s-%s", name, System.currentTimeMillis());
    }

    private boolean acquireHashProcessingLock(String fileHash, String fileName) {
        boolean locked = imageHashStateRepository.tryAcquireProcessing(fileHash, HASH_PROCESSING_TTL_MINUTES, TimeUnit.MINUTES);
        ImageHashAcquireOutcome outcome = ImageHashStatePolicy.evaluateAcquireResult(
                locked,
                locked ? null : imageHashStateRepository.findStatus(fileHash).map(ImageHashStatus::value).orElse(null)
        );
        if (outcome.type() == ImageHashAcquireOutcomeType.ALLOW) {
            return true;
        }
        if (outcome.type() == ImageHashAcquireOutcomeType.REJECT_DUPLICATE) {
            log.info("Duplicate image hash [{}] [{}]", fileHash, fileName);
            throw new RuntimeException(outcome.message());
        }
        if (outcome.type() == ImageHashAcquireOutcomeType.REJECT_PROCESSING) {
            log.info("Image is processing [{}] [{}]", fileHash, fileName);
            return false;
        }
        if (outcome.type() == ImageHashAcquireOutcomeType.RETRY_FROM_FAILED) {
            imageHashStateRepository.delete(fileHash);
            boolean retryLocked = imageHashStateRepository.tryAcquireProcessing(fileHash, HASH_PROCESSING_TTL_MINUTES, TimeUnit.MINUTES);
            if (retryLocked) {
                log.info("Retry processing for failed image hash [{}] [{}]", fileHash, fileName);
                return true;
            }
        }
        return false;
    }

    private void markHashStatus(String fileHash, ImageHashStatus status, long ttl, TimeUnit unit) {
        imageHashStateRepository.markStatus(fileHash, status, ttl, unit);
    }

    private void processTask(String taskId) {
        String lockValue = UUID.randomUUID().toString();
        if (!acquireTaskLock(taskId, lockValue)) {
            return;
        }

        try {
            BatchTask task = loadTask(taskId);
            if (task == null) {
                return;
            }

            if (!task.hasPendingItems()) {
                task.refreshSummary(System.currentTimeMillis());
                saveTask(task);
                return;
            }

            List<BatchTaskItem> pendingItems = task.pendingItems();
            if (pendingItems.isEmpty()) {
                task.refreshSummary(System.currentTimeMillis());
                saveTask(task);
                return;
            }

            List<CompletableFuture<Void>> futures = pendingItems.stream()
                    .map(item -> CompletableFuture.runAsync(() -> processTaskItem(task, taskId, item.getItemId()), embedTaskExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            synchronized (task) {
                task.refreshSummary(System.currentTimeMillis());
                saveTask(task);
            }
        } finally {
            releaseTaskLock(taskId, lockValue);
        }
    }

    private void processTaskItem(BatchTask task, String taskId, String itemId) {
        BatchProcessDTO request;
        String fileName;

        synchronized (task) {
            task.markItemRunning(itemId, System.currentTimeMillis());
            BatchTaskItem item = task.findItemOrThrow(itemId);
            request = batchTaskAssembler.toBatchProcessDTO(item);
            fileName = item.getFileName();
            saveTask(task);
        }

        try {
            ImageDocument doc = processSingleItem(request);
            boolean saved = saveSingleDoc(doc);
            synchronized (task) {
                if (saved) {
                    task.markItemSuccess(itemId, System.currentTimeMillis());
                } else {
                    task.markItemFailed(itemId, "Database writes failed", System.currentTimeMillis());
                }
                saveTask(task);
            }
        } catch (Exception e) {
            log.warn("task [{}] item [{}] failed: {}", taskId, fileName, e.getMessage());
            synchronized (task) {
                task.markItemFailed(itemId, e.getMessage(), System.currentTimeMillis());
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
                markHashStatus(doc.getFileHash(), ImageHashStatus.FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
                return false;
            }

            markHashStatus(doc.getFileHash(), ImageHashStatus.SUCCESS, HASH_SUCCESS_TTL_DAYS, TimeUnit.DAYS);
            return true;
        } catch (Exception e) {
            log.error("ES writes failed in async task", e);
            markHashStatus(doc.getFileHash(), ImageHashStatus.FAILED, HASH_FAILED_TTL_MINUTES, TimeUnit.MINUTES);
            return false;
        }
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

    private void saveTask(BatchTask task) {
        redisTemplate.opsForValue().set(
                BATCH_TASK_CACHE_PREFIX + task.getTaskId(),
                serializeTask(batchTaskAssembler.toTaskDto(task)),
                BATCH_TASK_TTL_HOURS,
                TimeUnit.HOURS
        );
    }

    private BatchTask loadTask(String taskId) {
        String raw = redisTemplate.opsForValue().get(BATCH_TASK_CACHE_PREFIX + taskId);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            BatchTaskStatusDTO dto = objectMapper.readValue(raw, BatchTaskStatusDTO.class);
            return batchTaskAssembler.toTaskDomain(dto);
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

    private List<GraphTriple> toDomainTriples(List<GraphTriple> triples) {
        if (triples == null || triples.isEmpty()) {
            return Collections.emptyList();
        }
        return triples.stream()
                .filter(Objects::nonNull)
                .map(t -> new GraphTriple(t.getS(), t.getP(), t.getO()))
                .toList();
    }
}
