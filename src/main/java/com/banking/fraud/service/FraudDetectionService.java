package com.banking.fraud.service;

import com.banking.fraud.ml.AnomalyDetectionService;
import com.banking.fraud.model.FraudAlert;
import com.banking.fraud.model.FraudDecisionRequest;
import com.banking.fraud.model.FraudSeverity;
import com.banking.fraud.repository.FraudAlertRepository;
import com.banking.fraud.rule.FraudRule;
import com.banking.fraud.rule.RuleEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FraudDetectionService {

    private final RuleEngine ruleEngine;
    private final AnomalyDetectionService anomalyService;
    private final VelocityService velocityService;
    private final FraudAlertRepository fraudAlertRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${fraud.actions.block-threshold:0.9}")
    private double blockThreshold;

    @Value("${fraud.actions.alert-threshold:0.6}")
    private double alertThreshold;

    @Value("${fraud.actions.review-threshold:0.3}")
    private double reviewThreshold;

    @Value("${kafka.topics.fraud-alerts:fraud-alerts}")
    private String fraudAlertsTopic;

    public FraudDetectionService(
            RuleEngine ruleEngine,
            AnomalyDetectionService anomalyService,
            VelocityService velocityService,
            FraudAlertRepository fraudAlertRepository,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.ruleEngine = ruleEngine;
        this.anomalyService = anomalyService;
        this.velocityService = velocityService;
        this.fraudAlertRepository = fraudAlertRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Main fraud detection method - evaluates both rule-based and ML approaches
     */
    public FraudAlert evaluateTransaction(FraudDecisionRequest request) {
        // 1. Evaluate rule-based checks
        List<FraudRule.RuleMatch> ruleMatches = ruleEngine.evaluate(request);
        double rulesScore = ruleEngine.calculateRulesScore(ruleMatches);

        // 2. Check velocity (Redis-based)
        boolean velocityOk = velocityService.checkVelocity(request.getAccountId());
        if (!velocityOk) {
            // Add velocity rule violation
            FraudRule.RuleMatch velocityMatch = new FraudRule.RuleMatch(
                "R003", "High Velocity", 0.5,
                "Transaction velocity exceeds limit");
            ruleMatches.add(velocityMatch);
            rulesScore = Math.max(rulesScore, 0.5);
        }

        // 3. ML anomaly detection
        int hourOfDay = LocalDateTime.now().getHour();
        double velocity = velocityService.getCurrentVelocity(request.getAccountId());
        double mlScore = anomalyService.detectAnomaly(
            request.getAccountId(),
            request.getAmount().doubleValue(),
            request.getCountry(),
            request.getChannel(),
            hourOfDay,
            velocity
        );

        // 4. Combine scores (weighted average: 60% rules, 40% ML)
        double combinedScore = (rulesScore * 0.6) + (mlScore * 0.4);
        BigDecimal riskScore = BigDecimal.valueOf(combinedScore).setScale(4, RoundingMode.HALF_UP);

        // 5. Determine decision
        String decision = makeDecision(combinedScore);
        FraudSeverity severity = determineSeverity(combinedScore);

        // 6. Build triggered rules string
        String triggeredRules = ruleMatches.stream()
            .map(FraudRule.RuleMatch::getRuleId)
            .collect(Collectors.joining(","));

        // 7. Create alert
        FraudAlert alert = new FraudAlert();
        alert.setAlertId(UUID.randomUUID().toString());
        alert.setTransactionId(request.getTransactionId());
        alert.setAccountId(request.getAccountId());
        alert.setRiskScore(riskScore);
        alert.setDecision(decision);
        alert.setSeverity(severity);
        alert.setTriggeredRules(triggeredRules);
        alert.setModelScore(BigDecimal.valueOf(mlScore).setScale(4, RoundingMode.HALF_UP));
        alert.setRulesScore(BigDecimal.valueOf(rulesScore).setScale(4, RoundingMode.HALF_UP));
        alert.setDescription(buildDescription(ruleMatches, mlScore, combinedScore));

        // 8. Save to DB (primary record)
        fraudAlertRepository.save(alert);
        
        // 9. Publish to Kafka (best effort, non-blocking for DB transaction)
        try {
            publishAlert(alert);
        } catch (Exception e) {
            // Log but don't fail the transaction - alert is already saved
            System.err.println("Failed to publish alert to Kafka: " + e.getMessage());
        }


        return alert;
    }

    private String makeDecision(double score) {
        if (score >= blockThreshold) return "BLOCK";
        if (score >= alertThreshold) return "ALERT";
        if (score >= reviewThreshold) return "REVIEW";
        return "APPROVE";
    }

    private FraudSeverity determineSeverity(double score) {
        if (score >= 0.9) return FraudSeverity.CRITICAL;
        if (score >= 0.7) return FraudSeverity.HIGH;
        if (score >= 0.4) return FraudSeverity.MEDIUM;
        return FraudSeverity.LOW;
    }

    private String buildDescription(List<FraudRule.RuleMatch> matches, double mlScore, double combinedScore) {
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("Combined risk score: %.4f (ML: %.4f, Rules: %.4f). ",
            combinedScore, mlScore,
            matches.stream().mapToDouble(FraudRule.RuleMatch::getScore).average().orElse(0)));

        if (!matches.isEmpty()) {
            desc.append("Triggered rules: ");
            desc.append(matches.stream()
                .map(FraudRule.RuleMatch::getRuleName)
                .collect(Collectors.joining(", ")));
        }
        return desc.toString();
    }

    @Async
    public void publishAlert(FraudAlert alert) {
        kafkaTemplate.send(fraudAlertsTopic, alert.getTransactionId(), alert);
    }
}