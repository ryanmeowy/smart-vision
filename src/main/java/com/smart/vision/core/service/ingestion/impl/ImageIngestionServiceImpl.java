package com.smart.vision.core.service.ingestion.impl;

import com.smart.vision.core.component.EsBatchTemplate;
import com.smart.vision.core.manager.AliyunOcrManager;
import com.smart.vision.core.manager.BailianEmbeddingManager;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_PRESIGNED_URL_VALIDITY_TIME;
import static com.smart.vision.core.constant.CommonConstant.ES_STORE_STAGE_FAIL;

/**
 * Image data processing service implementation
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
    private final ImageRepository imageRepository;
    private final EsBatchTemplate esBatchTemplate;
    private final Executor embedTaskExecutor;
    private final Executor imageUploadTaskExecutor;


    public void processAndIndex(MultipartFile file) throws Exception {
        imageRepository.save(processSingleFile(file));
    }

    public BatchUploadResultDTO batchProcess(MultipartFile[] files) {
        long startTime = System.currentTimeMillis();
        int totalCount = files.length;

        // 1. Define result collectors (thread-safe)
        List<ImageDocument> successDocs = Collections.synchronizedList(new ArrayList<>());
        List<BatchUploadResultDTO.BatchFailureItem> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCounter = new AtomicInteger(0);

        log.info("Starting batch processing task, total files: {}", totalCount);

        // 2. Parallel orchestration: use CompletableFuture to initiate concurrent tasks
        List<CompletableFuture<Void>> futures = Arrays.stream(files)
                .map(file -> CompletableFuture.runAsync(() -> {
                    String filename = file.getOriginalFilename();
                    try {
                        // Execute single image processing logic and get entity
                        ImageDocument doc = processSingleFile(file);
                        successDocs.add(doc);
                        successCounter.incrementAndGet();
                    } catch (Exception e) {
                        log.error("File [{}] processing exception: {}", filename, e.getMessage());
                        failures.add(BatchUploadResultDTO.BatchFailureItem.builder()
                                .filename(filename)
                                .errorMessage(e.getMessage())
                                .build());
                    }
                }, imageUploadTaskExecutor))
                .collect(Collectors.toList());

        // 3. Block and wait for all AI extraction and image upload tasks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 4. Batch write to Elasticsearch (using generic component)
        int finalSavedCount = 0;
        if (!successDocs.isEmpty()) {
            try {
                finalSavedCount = esBatchTemplate.bulkSave(successDocs);
            } catch (Exception e) {
                log.error("ES batch write phase crashed", e);
                // If ES is completely down, move originally successful items to failure list
                return handleTotalEsFailure(totalCount, successDocs, failures);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Batch task completed: success={}, failure={}, total time={}ms", finalSavedCount, failures.size(), duration);

        return BatchUploadResultDTO.builder()
                .total(totalCount)
                .successCount(finalSavedCount)
                .failureCount(totalCount - finalSavedCount)
                .failures(failures)
                .build();
    }

    private ImageDocument processSingleFile(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        String imagePath = ossManager.uploadFile(file);
        String tempUrl = ossManager.getPresignedUrl(imagePath, DEFAULT_PRESIGNED_URL_VALIDITY_TIME);
        CompletableFuture<List<Float>> vectorFuture = CompletableFuture
                .supplyAsync(() -> embeddingManager.embedImage(tempUrl), embedTaskExecutor);
        CompletableFuture<String> ocrFuture = CompletableFuture
                .supplyAsync(() -> ocrManager.extractText(tempUrl), embedTaskExecutor);
        CompletableFuture.allOf(vectorFuture, ocrFuture).join();
        ImageDocument doc = new ImageDocument();
        doc.setId(UUID.randomUUID().toString().replace("-", ""));
        doc.setImagePath(imagePath);
        doc.setFilename(filename);
        doc.setImageEmbedding(vectorFuture.get());
        doc.setOcrContent(ocrFuture.get());
        doc.setCreateTime(System.currentTimeMillis());
        return doc;
    }

    private BatchUploadResultDTO handleTotalEsFailure(int total, List<ImageDocument> successDocs, List<BatchUploadResultDTO.BatchFailureItem> failures) {
        successDocs.forEach(doc -> failures.add(BatchUploadResultDTO.BatchFailureItem.builder()
                .filename(doc.getFilename())
                .errorMessage(ES_STORE_STAGE_FAIL).build()));
        return BatchUploadResultDTO.builder().total(total).successCount(0).failureCount(total).failures(failures).build();
    }
}
