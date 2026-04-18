package com.banking.fraud.rule;

import java.util.function.Predicate;
import java.math.BigDecimal;

public class FraudRule {

    private final String id;
    private final String name;
    private final Predicate<FraudDecisionRequest> condition;
    private final double weight;
    private final String description;

    public FraudRule(String id, String name, Predicate<FraudDecisionRequest> condition,
                     double weight, String description) {
        this.id = id;
        this.name = name;
        this.condition = condition;
        this.weight = weight;
        this.description = description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Predicate<FraudDecisionRequest> getCondition() { return condition; }
    public double getWeight() { return weight; }
    public String getDescription() { return description; }

    // Nested class for rule match results
    public static class RuleMatch {
        private final String ruleId;
        private final String ruleName;
        private final double score;
        private final String description;

        public RuleMatch(String ruleId, String ruleName, double score, String description) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.score = score;
            this.description = description;
        }

        public String getRuleId() { return ruleId; }
        public String getRuleName() { return ruleName; }
        public double getScore() { return score; }
        public String getDescription() { return description; }
    }
}