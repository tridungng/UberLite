package com.uberlite.surgepricing.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Repository for surge pricing state in Redis.
 * <p>
 * Key namespacing:
 * - surge:pending:<h3Cell> → count of pending requests (incremented/decremented by Trip Service)
 * - surge:<h3Cell>:multiplier → cached multiplier value
 * - surge:<h3Cell>:multiplier:ts → timestamp of last update
 */
@Repository
public class SurgeRepository {
    private static final Logger logger = LoggerFactory.getLogger(SurgeRepository.class);
    private static final String PENDING_PREFIX = "surge:pending:";
    private static final String MULTIPLIER_PREFIX = "surge:";
    private static final String MULTIPLIER_SUFFIX = ":multiplier";
    private static final String TIMESTAMP_SUFFIX = ":multiplier:ts";
    private static final long MULTIPLIER_TTL_SECONDS = 15;

    private final RedisTemplate<String, String> redisTemplate;

    public SurgeRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Increment the pending request count for a cell.
     */
    public long incrementPendingRequests(String h3Cell) {
        String key = PENDING_PREFIX + h3Cell;
        Long result = redisTemplate.opsForValue().increment(key);
        logger.debug("Incremented pending requests for cell {}: {}", h3Cell, result);
        return result != null ? result : 0L;
    }

    /**
     * Decrement the pending request count for a cell (minimum 0).
     */
    public long decrementPendingRequests(String h3Cell) {
        String key = PENDING_PREFIX + h3Cell;
        Long result = redisTemplate.opsForValue().decrement(key);
        // Clamp to 0
        if (result != null && result < 0) {
            redisTemplate.opsForValue().set(key, "0");
            logger.debug("Clamped pending requests for cell {} to 0", h3Cell);
            return 0L;
        }
        logger.debug("Decremented pending requests for cell {}: {}", h3Cell, result);
        return result != null ? result : 0L;
    }

    /**
     * Get the current pending request count for a cell.
     */
    public long getPendingRequests(String h3Cell) {
        String key = PENDING_PREFIX + h3Cell;
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    /**
     * Cache a surge multiplier for a cell with a 15-second TTL.
     */
    public void cacheMultiplier(String h3Cell, double multiplier, long timestampMs) {
        String multiplierKey = MULTIPLIER_PREFIX + h3Cell + MULTIPLIER_SUFFIX;
        String timestampKey = MULTIPLIER_PREFIX + h3Cell + TIMESTAMP_SUFFIX;

        redisTemplate.opsForValue().set(multiplierKey, String.valueOf(multiplier), MULTIPLIER_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(timestampKey, String.valueOf(timestampMs), MULTIPLIER_TTL_SECONDS, TimeUnit.SECONDS);
        logger.debug("Cached multiplier {} for cell {} with TTL {}s", multiplier, h3Cell, MULTIPLIER_TTL_SECONDS);
    }

    /**
     * Get a cached surge multiplier for a cell, if available and not expired.
     */
    public Double getCachedMultiplier(String h3Cell) {
        String key = MULTIPLIER_PREFIX + h3Cell + MULTIPLIER_SUFFIX;
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            logger.debug("Cache hit for multiplier in cell {}", h3Cell);
            return Double.parseDouble(val);
        }
        logger.debug("Cache miss for multiplier in cell {}", h3Cell);
        return null;
    }

    /**
     * Get the timestamp of the cached multiplier, if available.
     */
    public Long getCachedMultiplierTimestamp(String h3Cell) {
        String key = MULTIPLIER_PREFIX + h3Cell + TIMESTAMP_SUFFIX;
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : null;
    }
}
