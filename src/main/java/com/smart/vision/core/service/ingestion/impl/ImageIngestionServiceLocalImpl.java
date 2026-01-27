package com.smart.vision.core.service.ingestion.impl;

import com.smart.vision.core.component.EsBatchTemplate;
import com.smart.vision.core.model.dto.BatchProcessDTO;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * image ingestion service local implementation
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class ImageIngestionServiceLocalImpl implements ImageIngestionService {

    private final EsBatchTemplate esBatchTemplate;
    private final GeneralImageIngestion generalImageIngestion;

    public BatchUploadResultDTO processBatchItems(List<BatchProcessDTO> items) {
        Set<String> seenHashes = new HashSet<>();
        List<BatchProcessDTO> uniqueItems = items.stream()
                .filter(x -> Objects.nonNull(x.getFileHash()))
                .filter(x -> seenHashes.add(x.getFileHash()))
                .toList();

        List<ImageDocument> successDocs = new ArrayList<>();
        List<BatchUploadResultDTO.BatchFailureItem> failures = new ArrayList<>();

        uniqueItems.forEach(item -> {
            try {
                generalImageIngestion.processSingleItem(item, successDocs);
            } catch (Exception e) {
                log.warn("image processing failed [{}]: {}", item.getFileName(), e.getMessage());
                failures.add(BatchUploadResultDTO.BatchFailureItem.builder()
                        .objectKey(item.getKey())
                        .filename(item.getFileName())
                        .errorMessage(e.getMessage())
                        .build());
            }
        });

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
}
