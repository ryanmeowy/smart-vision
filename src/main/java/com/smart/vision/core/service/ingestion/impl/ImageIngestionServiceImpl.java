package com.smart.vision.core.service.ingestion.impl;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.smart.vision.core.component.EsBatchTemplate;
import com.smart.vision.core.manager.AliyunOcrManager;
import com.smart.vision.core.manager.BailianEmbeddingManager;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.dto.BatchProcessRequest;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
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

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_PRESIGNED_URL_VALIDITY_TIME;

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

    /**
     * Processes a batch of image items concurrently for vector indexing and storage
     * and provides detailed error reporting for failed items
     *
     * @param items list of batch process requests containing OSS keys and metadata
     * @return BatchUploadResultDTO containing processing statistics and detailed failure information
     */
    public BatchUploadResultDTO processBatchItems(List<BatchProcessRequest> items) {
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
     * @param item the batch process request containing OSS key and file metadata
     * @param successDocs synchronized list to collect successfully processed documents
     * @throws NoApiKeyException if AI service API key is missing or invalid
     * @throws UploadFileException if there's an error accessing the image file
     */
    private void processSingleItem(BatchProcessRequest item, List<ImageDocument> successDocs) throws NoApiKeyException, UploadFileException {
        String tempUrl = ossManager.getPresignedUrl(item.getKey(), DEFAULT_PRESIGNED_URL_VALIDITY_TIME);
        List<Float> vector = embeddingManager.embedImage(tempUrl);
        String ocrText = ocrManager.extractText(tempUrl);
        ImageDocument doc = new ImageDocument();
        doc.setId(UUID.randomUUID().toString().replace("-", ""));
        doc.setImagePath(item.getKey());
        doc.setFilename(item.getFileName());
        doc.setImageEmbedding(vector);
        doc.setOcrContent(ocrText);
        doc.setCreateTime(System.currentTimeMillis());
        successDocs.add(doc);
    }


}
