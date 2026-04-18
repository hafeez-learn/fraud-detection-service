package com.banking.fraud.ml;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnomalyDetectionService {

    @Value("${fraud.ml.anomaly-threshold:0.75}")
    private double anomalyThreshold;

    private final StringRedisTemplate redisTemplate;
    private final Map<String, Double> localBaselineCache = new ConcurrentHashMap<>();

    public AnomalyDetectionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Detects anomaly score for a transaction based on features:
     * - Amount
     * - Time of day
     * - Geographic distance from last transaction
     * - Velocity
     * - Device risk score
     */
    public double detectAnomaly(String accountId, double amount, String country,
                                String channel, int hourOfDay, double velocity) {
        // Calculate anomaly score based on multiple factors
        double score = 0.0;

        // Amount anomaly (deviation from account baseline)
        double baseline = getAccountBaseline(accountId);
        if (amount > baseline * 3) {
            score += 0.3;
        } else if (amount > baseline * 2) {
            score += 0.15;
        }

        // Time-based anomaly (unusual hours)
        if (hourOfDay >= 0 && hourOfDay < 6) {
            score += 0.2; // Late night transactions are higher risk
        }

        // Channel risk
        switch (channel) {
            case "ONLINE": score += 0.15; break;
            case "ATM": score += 0.1; break;
            case "POS": score += 0.05; break;
            case "MOBILE": score += 0.02; break;
        }

        // Velocity anomaly
        if (velocity > 5) {
            score += 0.25;
        } else if (velocity > 3) {
            score += 0.1;
        }

        // Country risk
        if (Arrays.asList("NK", "IR", "SY", "YE").contains(country)) {
            score += 0.3;
        }

        return Math.min(score, 1.0); // Cap at 1.0
    }

    /**
     * Gets account baseline from Redis (with local cache fallback)
     */
    private double getAccountBaseline(String accountId) {
        String cacheKey = "baseline:" + accountId;
        String cached = (String) redisTemplate.opsForHash().get(cacheKey, "avg");
        if (cached != null) {
            return Double.parseDouble(cached);
        }
        return localBaselineCache.getOrDefault(accountId, 500.0);
    }


    /**
     * Updates account baseline after successful transaction
     */
    public void updateBaseline(String accountId, double amount) {
        String cacheKey = "baseline:" + accountId;
        String countKey = "baseline:" + accountId + ":count";

        
        // Get current values from Redis
        String currentAvgStr = (String) redisTemplate.opsForHash().get(cacheKey, "avg");
        String countStr = redisTemplate.opsForValue().get(countKey);
        
        double currentAvg = currentAvgStr != null ? Double.parseDouble(currentAvgStr) : 0.0;
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        
        // Calculate new rolling average
        double newAvg = (currentAvg * count + amount) / (count + 1);
        
        // Store in Redis
        redisTemplate.opsForHash().put(cacheKey, "avg", String.valueOf(newAvg));
        redisTemplate.opsForValue().set(countKey, String.valueOf(count + 1));
        
        // Also update local cache for fallback
        localBaselineCache.put(accountId, newAvg);
    }

    public boolean isAnomaly(double score) {
        return score >= anomalyThreshold;
    }

    public double getThreshold() {
        return anomalyThreshold;
    }
}