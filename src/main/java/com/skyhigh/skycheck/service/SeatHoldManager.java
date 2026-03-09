package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.config.ApplicationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Manages seat holds using Redis TTL for automatic expiration.
 * Redis key pattern: seat:hold:{seatId} -> passengerId
 * TTL is set to 120 seconds from hold creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatHoldManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationConfig appConfig;

    private static final String HOLD_KEY_PREFIX = "seat:hold:";

    /**
     * Create a hold for a seat with TTL
     */
    public boolean createHold(Long seatId, Long passengerId) {
        String key = getHoldKey(seatId);

        // Use SETNX (SET if Not eXists) to ensure atomic operation
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, passengerId, appConfig.getSeatHold().getTtlSeconds(), TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(success)) {
            log.info("Created hold for seat {} by passenger {} with TTL {} seconds",
                    seatId, passengerId, appConfig.getSeatHold().getTtlSeconds());
            return true;
        }

        log.warn("Failed to create hold for seat {} - already held", seatId);
        return false;
    }

    /**
     * Check if a seat is currently held
     */
    public boolean isHeld(Long seatId) {
        String key = getHoldKey(seatId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Get the passenger ID holding the seat
     */
    public Long getHoldingPassenger(Long seatId) {
        String key = getHoldKey(seatId);
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.valueOf(value.toString()) : null;
    }

    /**
     * Release a hold on a seat
     */
    public void releaseHold(Long seatId) {
        String key = getHoldKey(seatId);
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("Released hold for seat {}", seatId);
        }
    }

    /**
     * Get remaining TTL for a hold in seconds
     */
    public Long getRemainingTtl(Long seatId) {
        String key = getHoldKey(seatId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0L;
    }

    /**
     * Calculate expiration time for a hold
     */
    public LocalDateTime calculateExpirationTime() {
        return LocalDateTime.now().plusSeconds(appConfig.getSeatHold().getTtlSeconds());
    }

    /**
     * Verify if passenger owns the hold
     */
    public boolean verifyHoldOwnership(Long seatId, Long passengerId) {
        Long holdingPassenger = getHoldingPassenger(seatId);
        return holdingPassenger != null && holdingPassenger.equals(passengerId);
    }

    /**
     * Get hold key for Redis
     */
    private String getHoldKey(Long seatId) {
        return HOLD_KEY_PREFIX + seatId;
    }
}

