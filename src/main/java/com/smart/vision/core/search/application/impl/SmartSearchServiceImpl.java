package com.smart.vision.core.search.application.impl;

import com.smart.vision.core.integration.ai.port.MultiModalEmbeddingService;
import com.smart.vision.core.search.interfaces.assembler.ImageDocConvertor;
import com.smart.vision.core.common.exception.ApiError;
import com.smart.vision.core.common.exception.InfraException;
import com.smart.vision.core.search.application.support.HotSearchManager;
import com.smart.vision.core.integration.oss.OssManager;
import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.common.model.GraphTriple;
import com.smart.vision.core.search.interfaces.rest.dto.SearchQueryDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchExplainDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchResultDTO;
import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
import com.smart.vision.core.search.domain.model.StrategyTypeEnum;
import com.smart.vision.core.search.infrastructure.persistence.es.repository.ImageRepository;
import com.smart.vision.core.search.application.SmartSearchService;
import com.smart.vision.core.search.domain.strategy.RetrievalStrategy;
import com.smart.vision.core.search.domain.strategy.StrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.smart.vision.core.common.constant.AliyunConstant.X_OSS_PROCESS_EMBEDDING;
import static com.smart.vision.core.common.constant.CacheConstant.IMAGE_MD5_CACHE_PREFIX;
import static com.smart.vision.core.common.constant.CacheConstant.VECTOR_CACHE_PREFIX;
import static com.smart.vision.core.common.constant.CommonConstant.PROFILE_KEY_NAME;
import static com.smart.vision.core.common.constant.EmbeddingConstant.QUALITY_MIN_RESULTS;
import static com.smart.vision.core.common.constant.EmbeddingConstant.QUALITY_RATIO_CUTOFF;
import static com.smart.vision.core.common.constant.EmbeddingConstant.SIMILARITY_TOP_K;
import static com.smart.vision.core.integration.oss.domain.model.PresignedValidityEnum.SHORT_TERM_VALIDITY;
import static com.smart.vision.core.search.domain.util.ScoreUtil.mapScoreForSimilar;

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
    @Value("${app.embedding.image-input-mode:auto}")
    private String imageInputMode;
    @Value("${app.search.quality-absolute-min-score:0.72}")
    private double qualityAbsoluteMinScore;

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
        RetrievalStrategy strategy = strategyFactory.getStrategy(query.getSearchType());
        StrategyTypeEnum effectiveStrategy = strategy.getType();
        List<Float> queryVector = requiresTextEmbedding(effectiveStrategy) ? getOrCreateQueryVector(query.getKeyword()) : null;
        List<ImageSearchResultDTO> docs = strategy.search(query, queryVector);
        int maxResults = query.getLimit() != null && query.getLimit() > 0 ? query.getLimit() : docs.size();
        List<ImageSearchResultDTO> filteredDocs = shouldApplySimilarityFilter(effectiveStrategy)
                ? similarFilter(docs, maxResults)
                : docs.stream().limit(maxResults).collect(Collectors.toList());
        log.info("Search completed, number of results before filter: {}, after filter: {}", docs.size(), filteredDocs.size());
        boolean enableOcr = query.getEnableOcr() == null || query.getEnableOcr();
        List<ImageSearchResultDTO> reranked = shouldApplyManualRerank(effectiveStrategy)
                ? manualRerank(filteredDocs, query.getKeyword(), enableOcr)
                : filteredDocs;
        List<SearchResultDTO> dtoList = imageDocConvertor.convert2SearchResultDTO(reranked);
        applyVectorHitStatusForSearch(dtoList, reranked, query.getKeyword(), enableOcr, effectiveStrategy);
        applyExplainForSearch(dtoList, reranked, query.getKeyword(), enableOcr, effectiveStrategy);
        return dtoList;
    }

    public List<SearchResultDTO> searchSimilarById(String docId) {
        ImageDocument sourceDoc = imageRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Image does not exist or has been deleted"));

        List<Float> embedding = sourceDoc.getImageEmbedding();

        if (CollectionUtils.isEmpty(embedding)) {
            throw new RuntimeException("The image has not been vectorized yet");
        }
        List<ImageSearchResultDTO> similarDocs = imageRepository.searchSimilar(embedding, SIMILARITY_TOP_K, docId);
        List<ImageSearchResultDTO> filtered = similarFilter(similarDocs, SIMILARITY_TOP_K);
        applySimilarDisplayScore(filtered);
        List<SearchResultDTO> dtoList = imageDocConvertor.convert2SearchResultDTO(filtered);
        applyFixedVectorStatus(dtoList, "VECTOR_ONLY_LIKE");
        applyExplainForSearch(dtoList, filtered, "", false, StrategyTypeEnum.VECTOR_ONLY);
        return dtoList;
    }

    public List<ImageSearchResultDTO> similarFilter(List<ImageSearchResultDTO> results, int maxResults) {
        if (results.isEmpty()) return results;
        List<ImageSearchResultDTO> filtered = new ArrayList<>();
        int hardLimit = Math.max(1, Math.min(maxResults, results.size()));
        double prevKeptScore = -1d;

        for (int i = 0; i < hardLimit; i++) {
            ImageSearchResultDTO current = results.get(i);
            double currScore = decisionScore(current);

            if (currScore < qualityAbsoluteMinScore) {
                break;
            }

            if (filtered.isEmpty()) {
                filtered.add(current);
                prevKeptScore = currScore;
                continue;
            }

            boolean needBypassRatioCutoff = filtered.size() < QUALITY_MIN_RESULTS;
            if (!needBypassRatioCutoff) {
                boolean isBigDrop = prevKeptScore <= 0 || (currScore / prevKeptScore) < QUALITY_RATIO_CUTOFF;
                if (isBigDrop) {
                    break;
                }
            }

            filtered.add(current);
            prevKeptScore = currScore;
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
                if (shouldUseBytesInput()) {
                    vector = embeddingService.embedImage(file.getBytes(), file.getContentType());
                } else {
                    String objectKey = ossManager.uploadFile(file);
                    String tempAiUrl = ossManager.getAiPresignedUrl(objectKey, SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
                    vector = embeddingService.embedImage(tempAiUrl);
                }

                redisTemplate.opsForValue().set(cacheKey, vector, 24, TimeUnit.HOURS);
            }
            RetrievalStrategy strategy = strategyFactory.getStrategy(StrategyTypeEnum.IMAGE_TO_IMAGE.getCode());
            List<ImageSearchResultDTO> imageSearchResultDTOS = strategy.search(null, vector);
            int maxResults = limit > 0 ? limit : imageSearchResultDTOS.size();
            List<ImageSearchResultDTO> filtered = similarFilter(imageSearchResultDTOS, maxResults);
            applySimilarDisplayScore(filtered);
            List<SearchResultDTO> dtoList = imageDocConvertor.convert2SearchResultDTO(filtered);
            applyFixedVectorStatus(dtoList, "VECTOR_ONLY_LIKE");
            applyExplainForSearch(dtoList, filtered, "", false, StrategyTypeEnum.IMAGE_TO_IMAGE);
            return dtoList;
        } catch (Exception e) {
            log.error("Failed to search by image", e);
            throw new InfraException(ApiError.IMAGE_SEARCH_FAILED);
        }
    }

    private List<ImageSearchResultDTO> manualRerank(List<ImageSearchResultDTO> list, String keyword, boolean enableOcr) {
        if (!StringUtils.hasText(keyword) || CollectionUtils.isEmpty(list)) {
            return list;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);

        return list.stream()
                .sorted((o1, o2) -> {
                    int o1HitScore = keywordHitScore(o1, normalizedKeyword, enableOcr);
                    int o2HitScore = keywordHitScore(o2, normalizedKeyword, enableOcr);
                    if (o1HitScore != o2HitScore) {
                        return Integer.compare(o2HitScore, o1HitScore);
                    }
                    return Double.compare(decisionScore(o2), decisionScore(o1));
                })
                .collect(Collectors.toList());
    }

    private int keywordHitScore(ImageSearchResultDTO result, String normalizedKeyword, boolean enableOcr) {
        if (result == null || result.getDocument() == null) {
            return 0;
        }

        int score = 0;
        ImageDocument doc = result.getDocument();

        if (containsIgnoreCase(doc.getFileName(), normalizedKeyword)) {
            score += 3;
        }
        if (!CollectionUtils.isEmpty(doc.getTags())) {
            boolean tagHit = doc.getTags().stream().anyMatch(tag -> containsIgnoreCase(tag, normalizedKeyword));
            if (tagHit) {
                score += 2;
            }
        }
        if (enableOcr && containsIgnoreCase(doc.getOcrContent(), normalizedKeyword)) {
            score += 4;
        }
        return score;
    }

    private boolean containsIgnoreCase(String text, String normalizedKeyword) {
        return StringUtils.hasText(text)
                && StringUtils.hasText(normalizedKeyword)
                && text.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private double decisionScore(ImageSearchResultDTO result) {
        if (result == null) {
            return 0;
        }
        Double rawScore = result.getRawScore();
        if (rawScore != null) {
            return rawScore;
        }
        Double fallbackScore = result.getScore();
        return fallbackScore == null ? 0 : fallbackScore;
    }

    private boolean shouldUseBytesInput() {
        String mode = imageInputMode == null ? "auto" : imageInputMode.trim().toLowerCase(Locale.ROOT);
        if ("bytes".equals(mode)) {
            return true;
        }
        if ("url".equals(mode)) {
            return false;
        }
        String profile = System.getenv(PROFILE_KEY_NAME);
        return "local".equalsIgnoreCase(profile);
    }

    private List<Float> getOrCreateQueryVector(String keyword) {
        List<Float> queryVector = getVectorFromCache(keyword);
        if (CollectionUtils.isEmpty(queryVector)) {
            log.info("vector cache missed, processing new text, keyword: {}", keyword);
            queryVector = embeddingService.embedText(keyword);
            cacheVector(keyword, queryVector);
            return queryVector;
        }
        log.info("vector cache hit, processing new text, keyword: {}", keyword);
        return queryVector;
    }

    private boolean requiresTextEmbedding(StrategyTypeEnum strategyType) {
        return strategyType == StrategyTypeEnum.HYBRID || strategyType == StrategyTypeEnum.VECTOR_ONLY;
    }

    private boolean shouldApplySimilarityFilter(StrategyTypeEnum strategyType) {
        return false;
    }

    private boolean shouldApplyManualRerank(StrategyTypeEnum strategyType) {
        return strategyType != StrategyTypeEnum.HYBRID;
    }

    private void applySimilarDisplayScore(List<ImageSearchResultDTO> results) {
        if (CollectionUtils.isEmpty(results)) {
            return;
        }
        for (ImageSearchResultDTO result : results) {
            result.setScore(mapScoreForSimilar(result.getRawScore()));
        }
    }

    private void applyVectorHitStatusForSearch(List<SearchResultDTO> dtoList,
                                               List<ImageSearchResultDTO> sourceDocs,
                                               String keyword,
                                               boolean enableOcr,
                                               StrategyTypeEnum strategyType) {
        if (CollectionUtils.isEmpty(dtoList) || CollectionUtils.isEmpty(sourceDocs)) {
            return;
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int bound = Math.min(dtoList.size(), sourceDocs.size());

        for (int i = 0; i < bound; i++) {
            SearchResultDTO dto = dtoList.get(i);
            ImageSearchResultDTO source = sourceDocs.get(i);
            ImageDocument doc = source == null ? null : source.getDocument();
            dto.setVectorHitStatus(computeVectorHitStatus(doc, normalizedKeyword, enableOcr, strategyType));
        }
    }

    private void applyFixedVectorStatus(List<SearchResultDTO> dtoList, String status) {
        if (CollectionUtils.isEmpty(dtoList)) {
            return;
        }
        for (SearchResultDTO dto : dtoList) {
            dto.setVectorHitStatus(status);
        }
    }

    private void applyExplainForSearch(List<SearchResultDTO> dtoList,
                                       List<ImageSearchResultDTO> sourceDocs,
                                       String keyword,
                                       boolean enableOcr,
                                       StrategyTypeEnum strategyType) {
        if (CollectionUtils.isEmpty(dtoList) || CollectionUtils.isEmpty(sourceDocs)) {
            return;
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int bound = Math.min(dtoList.size(), sourceDocs.size());
        for (int i = 0; i < bound; i++) {
            SearchResultDTO dto = dtoList.get(i);
            ImageSearchResultDTO source = sourceDocs.get(i);
            ImageDocument doc = source == null ? null : source.getDocument();
            dto.setExplain(buildExplain(doc, normalizedKeyword, enableOcr, strategyType));
        }
    }

    private String computeVectorHitStatus(ImageDocument doc,
                                          String normalizedKeyword,
                                          boolean enableOcr,
                                          StrategyTypeEnum strategyType) {
        if (strategyType == StrategyTypeEnum.TEXT_ONLY) {
            return "TEXT_ONLY";
        }
        if (strategyType == StrategyTypeEnum.VECTOR_ONLY || strategyType == StrategyTypeEnum.IMAGE_TO_IMAGE) {
            return "VECTOR_ONLY_LIKE";
        }
        if (strategyType == StrategyTypeEnum.HYBRID) {
            boolean textHit = hasTextHit(doc, normalizedKeyword, enableOcr);
            return textHit ? "VECTOR_AND_TEXT" : "VECTOR_ONLY_LIKE";
        }
        return "TEXT_ONLY";
    }

    private SearchExplainDTO buildExplain(ImageDocument doc,
                                          String normalizedKeyword,
                                          boolean enableOcr,
                                          StrategyTypeEnum strategyType) {
        boolean vectorHit = strategyType == StrategyTypeEnum.HYBRID
                || strategyType == StrategyTypeEnum.VECTOR_ONLY
                || strategyType == StrategyTypeEnum.IMAGE_TO_IMAGE;
        boolean allowTextExplain = strategyType == StrategyTypeEnum.HYBRID || strategyType == StrategyTypeEnum.TEXT_ONLY;
        boolean filenameHit = allowTextExplain && hasFilenameHit(doc, normalizedKeyword);
        boolean ocrHit = allowTextExplain && hasOcrHit(doc, normalizedKeyword, enableOcr);
        boolean tagHit = allowTextExplain && hasTagHit(doc, normalizedKeyword);
        boolean graphHit = allowTextExplain && hasGraphHit(doc, normalizedKeyword);

        LinkedHashSet<String> hitSources = new LinkedHashSet<>();
        if (vectorHit) {
            hitSources.add("VECTOR");
        }
        if (ocrHit) {
            hitSources.add("OCR");
        }
        if (tagHit) {
            hitSources.add("TAG");
        }
        if (graphHit) {
            hitSources.add("GRAPH");
        }

        return SearchExplainDTO.builder()
                .strategyEffective(strategyType.getCode())
                .hitSources(new ArrayList<>(hitSources))
                .matchedBy(SearchExplainDTO.MatchedBy.builder()
                        .vector(vectorHit)
                        .filename(filenameHit)
                        .ocr(ocrHit)
                        .tag(tagHit)
                        .graph(graphHit)
                        .build())
                .build();
    }

    private boolean hasTextHit(ImageDocument doc, String normalizedKeyword, boolean enableOcr) {
        if (doc == null || !StringUtils.hasText(normalizedKeyword)) {
            return false;
        }
        if (hasOcrHit(doc, normalizedKeyword, enableOcr)) {
            return true;
        }
        if (hasTagHit(doc, normalizedKeyword)) {
            return true;
        }
        if (hasFilenameHit(doc, normalizedKeyword)) {
            return true;
        }
        if (hasGraphHit(doc, normalizedKeyword)) {
            return true;
        }
        return false;
    }

    private boolean hasFilenameHit(ImageDocument doc, String normalizedKeyword) {
        return doc != null && containsIgnoreCase(doc.getFileName(), normalizedKeyword);
    }

    private boolean hasOcrHit(ImageDocument doc, String normalizedKeyword, boolean enableOcr) {
        return enableOcr && doc != null && containsIgnoreCase(doc.getOcrContent(), normalizedKeyword);
    }

    private boolean hasTagHit(ImageDocument doc, String normalizedKeyword) {
        if (doc == null || CollectionUtils.isEmpty(doc.getTags())) {
            return false;
        }
        return doc.getTags().stream().anyMatch(tag -> containsIgnoreCase(tag, normalizedKeyword));
    }

    private boolean hasGraphHit(ImageDocument doc, String normalizedKeyword) {
        if (doc == null || CollectionUtils.isEmpty(doc.getRelations())) {
            return false;
        }
        for (GraphTriple triple : doc.getRelations()) {
            if (triple == null) {
                continue;
            }
            if (containsIgnoreCase(triple.getS(), normalizedKeyword)
                    || containsIgnoreCase(triple.getP(), normalizedKeyword)
                    || containsIgnoreCase(triple.getO(), normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }
}
