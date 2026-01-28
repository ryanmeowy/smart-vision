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
import java.util.concurrent.TimeUnit;

import static com.smart.vision.core.constant.CommonConstant.*;
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
    protected void processSingleItem(BatchProcessDTO item, List<ImageDocument> successDocs) {
        String tempUrl = ossManager.getAiPresignedUrl(item.getKey(), SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
        List<Float> vector = embeddingService.embedImage(tempUrl);
        String ocrText = imageOcrService.extractText(tempUrl);
        List<String> tags = contentGenerationService.generateTags(tempUrl);
        List<GraphTripleDTO> graphTriples = contentGenerationService.generateGraph(tempUrl);
        String cacheKey = String.format("%s%s:%s", HASH_INDEX_PREFIX, System.getenv("SPRING_PROFILES_ACTIVE"), item.getFileHash());
        if (redisTemplate.hasKey(cacheKey)) {
            log.info("Duplicate image hash [{}] [{}]", item.getFileHash(), item.getFileName());
            throw new RuntimeException("Duplicate image, skipped.");
        }
        redisTemplate.opsForValue().set(cacheKey, "1", 30, TimeUnit.DAYS);

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
        successDocs.add(doc);
    }

    protected String genFileName(String imageUrl) {
        String name = Strings.EMPTY;
        if (!StringUtils.hasText(imageUrl)) {
            name = DEFAULT_IMAGE_NAME;
        }
        name = contentGenerationService.generateFileName(imageUrl);
        return String.format("%s-%s", name, System.currentTimeMillis());
    }
}
