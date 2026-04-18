package com.banking.fraud.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class VelocityService {

    private final StringRedisTemplate redisTemplate;

    @Value("${fraud.rules.velocity-window-seconds:300}")
    private int velocityWindow;

    @Value("${fraud.rules.max-transactions-per-window:10}")
    private int maxTransactions;

    public VelocityService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Increment and check transaction velocity for an account
     * Returns true if velocity is within acceptable limits
     */
    public boolean checkVelocity(String accountId) {
        String key = "velocity:" + accountId;

        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount != null && currentCount == 1) {
            // First transaction, set expiration
            redisTemplate.expire(key, Duration.ofSeconds(velocityWindow));
        }

        return currentCount == null || currentCount <= maxTransactions;
    }

    /**
     * Get current transaction count for an account
     */
    public int getCurrentVelocity(String accountId) {
        String key = "velocity:" + accountId;
        String count = redisTemplate.opsForValue().get(key);
        return count != null ? Integer.parseInt(count) : 0;
    }

    /**
     * Get remaining transactions allowed in current window
     */
    public int getRemainingAllowance(String accountId) {
        int current = getCurrentVelocity(accountId);
        return Math.max(0, maxTransactions - current);
    }

    /**
     * Reset velocity counter (e.g., after manual review)
     */
    public void resetVelocity(String accountId) {
        String key = "velocity:" + accountId;
        redisTemplate.delete(key);
    }
}