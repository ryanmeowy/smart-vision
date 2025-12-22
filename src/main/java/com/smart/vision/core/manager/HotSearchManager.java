
package com.smart.vision.core.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.smart.vision.core.constant.CommonConstant.FALLBACK_WORDS;
import static com.smart.vision.core.constant.CommonConstant.HOT_SEARCH_KEY;
import static com.smart.vision.core.constant.CommonConstant.MAX_HOT_WORDS;
import static com.smart.vision.core.constant.CommonConstant.MOCK_BLOCKED_WORDS;


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
        if (keyword == null || keyword.trim().length() < 2) {
            return;
        }
        
        // Normalization: remove spaces, convert to lowercase (prevent "Red" and "red" from being counted separately)
        String normalizedWord = keyword.trim().toLowerCase();
        
        if (MOCK_BLOCKED_WORDS.contains(normalizedWord) || normalizedWord.length() > 20) {
            return;
        }

        try {
            // score +1
            stringRedisTemplate.opsForZSet().incrementScore(HOT_SEARCH_KEY, normalizedWord, 1.0);
        } catch (Exception e) {
            log.warn("Failed to count hot words: {}", e.getMessage());
        }
    }

    /**
     * Get Top N hot words
     */
    public List<String> getTopHotWords() {
        // ZREVRANGE: Retrieve 0 to 9 in descending order by score
        Set<String> words = stringRedisTemplate.opsForZSet()
                .reverseRange(HOT_SEARCH_KEY, 0, MAX_HOT_WORDS - 1);

        if (words == null || words.isEmpty()) {
            // Fallback data (used during cold start)
            return FALLBACK_WORDS;
        }
        return new ArrayList<>(words);
    }
}