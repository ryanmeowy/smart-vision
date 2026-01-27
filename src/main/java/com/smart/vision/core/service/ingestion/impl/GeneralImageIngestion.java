package com.smart.vision.core.service.ingestion.impl;

import com.smart.vision.core.ai.ContentGenerationService;
import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.ai.MultiModalEmbeddingService;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.smart.vision.core.constant.CommonConstant.HASH_INDEX_PREFIX;
import static com.smart.vision.core.constant.CommonConstant.X_OSS_PROCESS_EMBEDDING;
import static com.smart.vision.core.model.enums.PresignedValidityEnum.SHORT_TERM_VALIDITY;

/**
 * General image ingestion service
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralImageIngestion {
    private final OssManager ossManager;
    private final MultiModalEmbeddingService embeddingService;
    private final ImageOcrService imageOcrService;
    private final ContentGenerationService contentGenerationService;
    private final StringRedisTemplate redisTemplate;
    private final IdGen idGen;

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
