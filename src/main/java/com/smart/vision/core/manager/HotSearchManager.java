
package com.smart.vision.core.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.smart.vision.core.constant.CacheConstant.HOT_WORDS_CACHE_PREFIX;
import static com.smart.vision.core.constant.CommonConstant.PROFILE_KEY_NAME;
import static com.smart.vision.core.constant.SearchConstant.FALLBACK_WORDS;
import static com.smart.vision.core.constant.SearchConstant.MAX_HOT_WORDS;
import static com.smart.vision.core.constant.SearchConstant.MOCK_BLOCKED_WORDS;


/**
 * HotSearchManager is responsible for managing hot search keywords.
 * It provides functionality to record search terms and retrieve the top hot words.
 * The class uses Redis for storing and managing the hot search data.
 *
 * @author Ryan
 * @since 2025/12/22
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class HotSearchManager {

    private final StringRedisTemplate stringRedisTemplate;
    
    /**
     * Asynchronously record search terms (Fire and Forget)
     */
    @Async("hotWordsTaskExecutor")
    public void incrementScore(String keyword) {
        if (keyword == null) {
            return;
        }
        
        // Normalization: remove spaces, convert to lowercase (prevent "Red" and "red" from being counted separately)
        String normalizedWord = keyword.trim().toLowerCase();

        // mock blocked words, this can be replaced with a database query
        if (MOCK_BLOCKED_WORDS.contains(normalizedWord) || normalizedWord.length() > 20) {
            return;
        }

        try {
            String cacheKey = String.format("%s:%s", HOT_WORDS_CACHE_PREFIX, System.getenv(PROFILE_KEY_NAME));
            // score +1
            stringRedisTemplate.opsForZSet().incrementScore(cacheKey, normalizedWord, 1.0);
        } catch (Exception e) {
            log.warn("Failed to count hot words: {}", e.getMessage());
        }
    }

    /**
     * Get Top N hot words
     */
    public List<String> getTopHotWords() {
        String cacheKey = String.format("%s:%s", HOT_WORDS_CACHE_PREFIX, System.getenv(PROFILE_KEY_NAME));
        // ZREV RANGE: Retrieve 0 to 9 in descending order by score
        Set<String> words = stringRedisTemplate.opsForZSet()
                .reverseRange(cacheKey, 0, MAX_HOT_WORDS - 1);

        if (words == null || words.isEmpty()) {
            // Fallback data (used during cold start)
            return FALLBACK_WORDS;
        }
        return new ArrayList<>(words);
    }
}