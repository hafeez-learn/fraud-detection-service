package com.banking.fraud.ml;

import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.type.StructType;
import smile.data.type.StructField;
import smile.data.type.DataType;
import smile.vector.Vector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class AnomalyDetectionService {

    @Value("${fraud.ml.anomaly-threshold:0.75}")
    private double anomalyThreshold;

    private RandomForest model;
    private final Map<String, Double> accountBaseline = new HashMap<>();

    @PostConstruct
    public void init() {
        // Initialize with a simple mock model
        // In production, load pre-trained model from ${fraud.ml.model-path}
        initializeMockModel();
    }

    private void initializeMockModel() {
        // Mock model initialization
        // Real implementation would load: model = RandomForest.load(modelPath);
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
        double baseline = accountBaseline.getOrDefault(accountId, 500.0);
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

        // Country risk (simplified)
        if (List.of("NK", "IR", "SY", "YE").contains(country)) {
            score += 0.3;
        }

        return Math.min(score, 1.0); // Cap at 1.0
    }

    /**
     * Updates account baseline after successful transaction
     */
    public void updateBaseline(String accountId, double amount) {
        double current = accountBaseline.getOrDefault(accountId, 0.0);
        double count = accountBaseline.getOrDefault(accountId + "_count", 0.0);
        // Rolling average
        double newAvg = (current * count + amount) / (count + 1);
        accountBaseline.put(accountId, newAvg);
        accountBaseline.put(accountId + "_count", count + 1);
    }

    public boolean isAnomaly(double score) {
        return score >= anomalyThreshold;
    }

    public double getThreshold() {
        return anomalyThreshold;
    }
}