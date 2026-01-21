package com.smart.vision.core.service.ingestion.impl;

import cn.hutool.core.util.IdUtil;
import com.smart.vision.core.ai.ContentGenerationService;
import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.ai.MultiModalEmbeddingService;
import com.smart.vision.core.component.EsBatchTemplate;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.dto.BatchProcessDTO;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.smart.vision.core.constant.CommonConstant.HASH_INDEX_PREFIX;
import static com.smart.vision.core.constant.CommonConstant.X_OSS_PROCESS_EMBEDDING;
import static com.smart.vision.core.constant.CommonConstant.X_OSS_PROCESS_OCR;
import static com.smart.vision.core.model.enums.PresignedValidityEnum.SHORT_TERM_VALIDITY;

/**
 * Image data processing service implementation of ImageIngestionService that handles batch processing of images
 * for vector embedding generation, OCR text extraction, and Elasticsearch indexing
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageIngestionServiceImpl implements ImageIngestionService {
    private final OssManager ossManager;
    private final MultiModalEmbeddingService embeddingService;
    private final ImageOcrService imageOcrService;
    private final EsBatchTemplate esBatchTemplate;
    private final Executor embedTaskExecutor;
    private final ContentGenerationService contentGenerationService;
    private final StringRedisTemplate redisTemplate;
    

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
                        processSingleItem(item, successDocs);
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
                savedCount = esBatchTemplate.bulkSave(successDocs);
            } catch (Exception e) {
                log.error("ES writes failed", e);
                for (ImageDocument doc : successDocs) {
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

    /**
     * Processes a single image item through the complete pipeline
     *
     * @param item        the batch process request containing OSS key and file metadata
     * @param successDocs synchronized list to collect successfully processed documents
     */
    private void processSingleItem(BatchProcessDTO item, List<ImageDocument> successDocs) {
        String tempUrl = ossManager.getAiPresignedUrl(item.getKey(), SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
        List<Float> vector = embeddingService.embedImage(tempUrl);
        String ocrText = imageOcrService.extractText(tempUrl);
        List<String> tags = contentGenerationService.generateTags(tempUrl);

        if (redisTemplate.hasKey(HASH_INDEX_PREFIX + item.getFileHash())) {
            log.info("Duplicate image hash [{}] [{}]", item.getFileHash(), item.getFileName());
            throw new RuntimeException("Duplicate image, skipped.");
        }
        redisTemplate.opsForValue().set(HASH_INDEX_PREFIX + item.getFileHash(), "1");

        ImageDocument doc = new ImageDocument();
        doc.setId(IdUtil.getSnowflakeNextId());
        doc.setImagePath(item.getKey());
        doc.setRawFilename(item.getFileName());
        doc.setFileName(genFileName(tempUrl));
        doc.setImageEmbedding(vector);
        doc.setOcrContent(ocrText);
        doc.setCreateTime(System.currentTimeMillis());
        doc.setTags(tags);
        doc.setFileHash(item.getFileHash());
        successDocs.add(doc);
    }

    private String genFileName(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return Strings.EMPTY;
        }
        String name = contentGenerationService.generateFileName(imageUrl);
        if (!StringUtils.hasText(name)) {
            return Strings.EMPTY;
        }
        return String.format("%s-%s", name, System.currentTimeMillis());
    }

}
