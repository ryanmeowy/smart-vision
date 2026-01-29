package com.smart.vision.core.service.search.impl;

import com.google.common.collect.Lists;
import com.smart.vision.core.ai.MultiModalEmbeddingService;
import com.smart.vision.core.convertor.ImageDocConvertor;
import com.smart.vision.core.manager.HotSearchManager;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.model.enums.StrategyTypeEnum;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.service.search.SmartSearchService;
import com.smart.vision.core.strategy.RetrievalStrategy;
import com.smart.vision.core.strategy.StrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.AliyunConstant.X_OSS_PROCESS_EMBEDDING;
import static com.smart.vision.core.constant.CacheConstant.IMAGE_MD5_CACHE_PREFIX;
import static com.smart.vision.core.constant.CacheConstant.VECTOR_CACHE_PREFIX;
import static com.smart.vision.core.constant.CommonConstant.PROFILE_KEY_NAME;
import static com.smart.vision.core.constant.EmbeddingConstant.SIMILARITY_TOP_K;
import static com.smart.vision.core.model.enums.PresignedValidityEnum.SHORT_TERM_VALIDITY;

/**
 * Smart search service implementation
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartSearchServiceImpl implements SmartSearchService {
    private final MultiModalEmbeddingService embeddingService;
    private final ImageRepository imageRepository;
    private final ImageDocConvertor imageDocConvertor;
    private final HotSearchManager hotSearchManager;
    private final StrategyFactory strategyFactory;
    private final RedisTemplate<String, List<Float>> redisTemplate;
    private final OssManager ossManager;

    public List<SearchResultDTO> search(SearchQueryDTO query) {
        if (StringUtils.hasText(query.getKeyword())) {
            hotSearchManager.incrementScore(query.getKeyword());
        }
        List<Float> queryVector = getVectorFromCache(query.getKeyword());
        if (CollectionUtils.isEmpty(queryVector)) {
            log.info("vector cache missed, processing new text, keyword: {}", query.getKeyword());
            queryVector = embeddingService.embedText(query.getKeyword());
            cacheVector(query.getKeyword(), queryVector);
        }
        log.info("vector cache hit, processing new text, keyword: {}", query.getKeyword());
        RetrievalStrategy strategy = strategyFactory.getStrategy(query.getSearchType());
        List<ImageSearchResultDTO> docs = strategy.search(query, queryVector);
        log.info("Search completed, number of results: {}", docs.size());
        return imageDocConvertor.convert2SearchResultDTO(manualRerank(docs, query.getKeyword()));
    }

    public List<SearchResultDTO> searchByVector(String docId) {
        ImageDocument sourceDoc = imageRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Image does not exist or has been deleted"));

        List<Float> embedding = sourceDoc.getImageEmbedding();

        if (CollectionUtils.isEmpty(embedding)) {
            throw new RuntimeException("The image has not been vectorized yet");
        }
        // Perform search (find the 10 most similar images)
        List<ImageSearchResultDTO> similarDocs = imageRepository.searchSimilar(embedding, SIMILARITY_TOP_K, docId);
        return imageDocConvertor.convert2SearchResultDTO(similarFilter(similarDocs));
    }

    public List<ImageSearchResultDTO> similarFilter(List<ImageSearchResultDTO> results) {
        if (results.isEmpty()) return results;
        List<ImageSearchResultDTO> filtered = new ArrayList<>();
        if (results.getFirst().getScore() < 0.7) return filtered;
        filtered.add(results.getFirst());
        for (int i = 1; i < results.size(); i++) {
            double prevScore = results.get(i - 1).getScore();
            double currScore = results.get(i).getScore();

            // Relative drop: if the current score drops by 40% compared to the previous one (ratio < 0.6)
            // Absolute drop: or directly falls below 0.4 (hard limit)
            boolean isBigDrop = (currScore / prevScore) < 0.6;
            boolean isTooLow = currScore < 0.4;

            if (isBigDrop || isTooLow) {
                break;
            }
            filtered.add(results.get(i));
        }
        return filtered;
    }

    private List<Float> getVectorFromCache(String text) {
        if (!StringUtils.hasText(text)) return null;
        String key = buildVectorCacheKey(text);
        return redisTemplate.opsForValue().get(key);
    }

    private void cacheVector(String text, List<Float> vector) {
        if (!StringUtils.hasText(text) || vector == null || vector.isEmpty()) return;
        String key = buildVectorCacheKey(text);
        redisTemplate.opsForValue().set(key, vector, 24, TimeUnit.HOURS);
    }

    private String buildVectorCacheKey(String text) {
        return String.format("%s%s:%s", VECTOR_CACHE_PREFIX, System.getenv(PROFILE_KEY_NAME), DigestUtils.md5DigestAsHex(text.trim().toLowerCase().getBytes()));
    }

    @Override
    public List<SearchResultDTO> searchByImage(MultipartFile file, int limit) {
        try (InputStream is = file.getInputStream()) {
            String md5 = DigestUtils.md5DigestAsHex(is);
            String cacheKey = String.format("%s%s:%s", IMAGE_MD5_CACHE_PREFIX, System.getenv(PROFILE_KEY_NAME), md5);
            List<Float> vector = redisTemplate.opsForValue().get(cacheKey);
            if (vector != null) {
                log.info("Cache hit, MD5: {}", md5);
            } else {
                log.info("Cache missed, processing new image, MD5: {}", md5);
                String objectKey = ossManager.uploadFile(file);
                String tempAiUrl = ossManager.getAiPresignedUrl(objectKey, SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
                vector = embeddingService.embedImage(tempAiUrl);

                redisTemplate.opsForValue().set(cacheKey, vector, 24, TimeUnit.HOURS);
            }
            RetrievalStrategy strategy = strategyFactory.getStrategy(StrategyTypeEnum.IMAGE_TO_IMAGE.getCode());
            List<ImageSearchResultDTO> imageSearchResultDTOS = strategy.search(null, vector);
            return imageDocConvertor.convert2SearchResultDTO(imageSearchResultDTOS);
        } catch (Exception e) {
            log.error("Failed to search by image", e);
            return Lists.newArrayList();
        }
    }

    private List<ImageSearchResultDTO> manualRerank(List<ImageSearchResultDTO> list, String keyword) {
        return list.stream()
                .sorted((o1, o2) -> {
                    boolean o1Hit = null != o1.getDocument().getOcrContent() && o1.getDocument().getOcrContent().contains(keyword);
                    boolean o2Hit = null != o2.getDocument().getOcrContent() && o2.getDocument().getOcrContent().contains(keyword);
                    if (o1Hit && !o2Hit) return -1;
                    if (!o1Hit && o2Hit) return 1;
                    return Double.compare(o2.getScore(), o1.getScore());
                })
                .collect(Collectors.toList());
    }
}