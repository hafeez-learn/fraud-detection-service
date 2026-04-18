package com.banking.fraud.rule;

import com.banking.fraud.model.FraudDecisionRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RuleEngine {

    private final List<FraudRule> rules;

    public RuleEngine() {
        this.rules = new ArrayList<>();
        initializeRules();
    }

    private void initializeRules() {
        // Rule 1: High Amount Detection
        rules.add(new FraudRule("R001", "High Amount Transaction",
            (tx) -> tx.getAmount().compareTo(new BigDecimal("5000")) > 0,
            0.4, "Transaction amount exceeds $5,000"));

        // Rule 2: Blacklisted Country
        rules.add(new FraudRule("R002", "Blacklisted Country",
            (tx) -> List.of("NK", "IR", "SY").contains(tx.getCountry()),
            0.9, "Transaction from blacklisted country"));

        // Rule 3: Velocity Check (handled separately with Redis)
        rules.add(new FraudRule("R003", "High Velocity",
            (tx) -> false, // Handled by VelocityService
            0.5, "Unusually high transaction frequency"));

        // Rule 4: New Device Check
        rules.add(new FraudRule("R004", "Unknown Device",
            (tx) -> tx.getDeviceId() != null && tx.getDeviceId().startsWith("NEW"),
            0.3, "Transaction from new/unrecognized device"));

        // Rule 5: Online Channel High Amount
        rules.add(new FraudRule("R005", "Online High Value",
            (tx) -> "ONLINE".equals(tx.getChannel()) &&
                    tx.getAmount().compareTo(new BigDecimal("1000")) > 0,
            0.35, "High-value online transaction"));

        // Rule 6: ATM International Cash Advance
        rules.add(new FraudRule("R006", "International ATM",
            (tx) -> "ATM".equals(tx.getChannel()) &&
                    tx.getAmount().compareTo(new BigDecimal("2000")) > 0,
            0.45, "International ATM withdrawal"));
    }

    public List<RuleMatch> evaluate(FraudDecisionRequest request) {
        List<RuleMatch> matches = new ArrayList<>();
        for (FraudRule rule : rules) {
            if (rule.getCondition().test(request)) {
                matches.add(new RuleMatch(rule.getId(), rule.getName(),
                    rule.getWeight(), rule.getDescription()));
            }
        }
        return matches;
    }

    public double calculateRulesScore(List<RuleMatch> matches) {
        if (matches.isEmpty()) return 0.0;

        // Weighted average based on rule weights
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        for (RuleMatch match : matches) {
            weightedSum += match.getWeight() * match.getScore();
            totalWeight += match.getWeight();
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }
}