package com.smart.vision.core.service.ingestion.impl;

import com.smart.vision.core.component.EsBatchTemplate;
import com.smart.vision.core.manager.AliyunOcrManager;
import com.smart.vision.core.manager.AliyunTaggingManager;
import com.smart.vision.core.manager.BailianEmbeddingManager;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.dto.BatchProcessDTO;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.smart.vision.core.constant.CommonConstant.DUPLICATE_THRESHOLD;
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
    private final BailianEmbeddingManager embeddingManager;
    private final AliyunOcrManager ocrManager;
    private final EsBatchTemplate esBatchTemplate;
    private final Executor embedTaskExecutor;
    private final ImageRepository imageRepository;
    private final AliyunTaggingManager aliyunTaggingManager;

    public BatchUploadResultDTO processBatchItems(List<BatchProcessDTO> items) {
        List<ImageDocument> successDocs = Collections.synchronizedList(new ArrayList<>());
        List<BatchUploadResultDTO.BatchFailureItem> failures = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = items.stream()
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
                            .filename(doc.getFilename())
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
    private void processSingleItem(BatchProcessDTO item, List<ImageDocument> successDocs) throws Exception {
        String tempUrl2Embed = ossManager.getAiPresignedUrl(item.getKey(), SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
        List<Float> vector = embeddingManager.embedImage(tempUrl2Embed);
        String tempUrl2OCR = ossManager.getAiPresignedUrl(item.getKey(), SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_OCR);
        String ocrText = ocrManager.extractText(tempUrl2OCR);
        // AI tag
        List<String> tags = aliyunTaggingManager.generateTags(tempUrl2OCR);

        // Set threshold to 0.98 (Highly similar)
        ImageDocument duplicate = imageRepository.findDuplicate(vector, DUPLICATE_THRESHOLD);
        if (duplicate != null) {
            log.info("Duplicate image detected: {} is highly similar to {} in the database, skipping storage", item.getFileName(), duplicate.getId());
            throw new RuntimeException("Duplicate image (Skipped)");
        }

        ImageDocument doc = new ImageDocument();
        doc.setId(UUID.randomUUID().toString().replace("-", ""));
        doc.setImagePath(item.getKey());
        doc.setFilename(item.getFileName());
        doc.setImageEmbedding(vector);
        doc.setOcrContent(ocrText);
        doc.setCreateTime(System.currentTimeMillis());
        doc.setTags(tags);
        successDocs.add(doc);
    }


}
