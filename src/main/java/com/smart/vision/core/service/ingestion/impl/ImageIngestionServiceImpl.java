package com.smart.vision.core.service.ingestion.impl;

import com.smart.vision.core.ai.ContentGenerationService;
import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.ai.MultiModalEmbeddingService;
import com.smart.vision.core.component.EsBatchTemplate;
import com.smart.vision.core.component.IdGen;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.dto.BatchProcessDTO;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import com.smart.vision.core.model.dto.GraphTripleDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final EsBatchTemplate esBatchTemplate;
    private final Executor embedTaskExecutor;
    private final OssManager ossManager;
    private final MultiModalEmbeddingService embeddingService;
    private final ImageOcrService imageOcrService;
    private final ContentGenerationService contentGenerationService;
    private final StringRedisTemplate redisTemplate;
    private final IdGen idGen;

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
     */
    protected ImageDocument processSingleItem(BatchProcessDTO item) {
        String tempUrl = ossManager.getAiPresignedUrl(item.getKey(), SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
        List<Float> vector = embeddingService.embedImage(tempUrl);
        String ocrText = imageOcrService.extractText(tempUrl);
        List<String> tags = contentGenerationService.generateTags(tempUrl);
        List<GraphTripleDTO> graphTriples = contentGenerationService.generateGraph(tempUrl);
        String cacheKey = String.format("%s%s:%s", HASH_INDEX_CACHE_PREFIX, System.getenv(PROFILE_KEY_NAME), item.getFileHash());
        Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(cacheKey, "1", 30, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(setIfAbsent)) {
            log.info("Duplicate image hash [{}] [{}]", item.getFileHash(), item.getFileName());
            throw new RuntimeException("Duplicate image, skipped.");
        }

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
    }

    protected String genFileName(String imageUrl) {
        String name = StringUtils.hasText(imageUrl) ? contentGenerationService.generateFileName(imageUrl) : DEFAULT_IMAGE_NAME;
        return String.format("%s-%s", name, System.currentTimeMillis());
    }
}
