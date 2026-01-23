package com.smart.vision.core.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.smart.vision.core.constant.CommonConstant.ID_GEN_KEY;
import static com.smart.vision.core.constant.CommonConstant.ID_GEN_MAX_ID;
import static com.smart.vision.core.constant.CommonConstant.ID_GEN_MAX_STEP;
import static com.smart.vision.core.constant.CommonConstant.ID_GEN_MIN_ID;
import static com.smart.vision.core.constant.CommonConstant.ID_GEN_MIN_STEP;
import static com.smart.vision.core.constant.CommonConstant.ID_GEN_SEGMENT_SIZE;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdGen {

    private final StringRedisTemplate stringRedisTemplate;

    private final SecureRandom random = new SecureRandom();

    private final Lock lock = new ReentrantLock();

    private String cacheKey;

    private long currentSegmentMaxId = -1;

    private long currentId = -1;

    @PostConstruct
    public void init() {
        this.cacheKey = String.format("%s:%s", ID_GEN_KEY, System.getenv("SPRING_PROFILES_ACTIVE"));
        stringRedisTemplate.opsForValue().setIfAbsent(cacheKey, String.valueOf(ID_GEN_MIN_ID));
    }

    public long nextId() {
        int step = random.nextInt(ID_GEN_MAX_STEP - ID_GEN_MIN_STEP + 1) + ID_GEN_MIN_STEP;

        lock.lock();
        try {
            if (currentId < 0 || (currentId + step) > currentSegmentMaxId) {
                loadNextSegment();
            }
            currentId += step;
            if (currentId > ID_GEN_MAX_ID) {
                throw new RuntimeException("ID Space Exhausted: Global limit reached.");
            }
            return currentId;
        } finally {
            lock.unlock();
        }
    }

    private void loadNextSegment() {
        try {
            Long newMaxId = stringRedisTemplate.opsForValue().increment(cacheKey, ID_GEN_SEGMENT_SIZE);
            if (newMaxId == null || newMaxId > ID_GEN_MAX_ID) {
                throw new RuntimeException("ID Space Exhausted: Global limit reached.");
            }
            currentSegmentMaxId = newMaxId;
            currentId = newMaxId - ID_GEN_SEGMENT_SIZE;
            log.info("Loaded new ID segment. Range: [{}, {}]", currentId, currentSegmentMaxId);
        } catch (Exception e) {
            log.error("Failed to load ID segment from Redis", e);
            throw new RuntimeException("ID Generation Service Unavailable", e);
        }
    }
}