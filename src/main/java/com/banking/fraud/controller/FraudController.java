package com.banking.fraud.controller;

import com.banking.fraud.model.FraudAlert;
import com.banking.fraud.model.FraudDecisionRequest;
import com.banking.fraud.service.FraudDetectionService;
import com.banking.fraud.repository.FraudAlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fraud")
@CrossOrigin(origins = "*")
public class FraudController {

    private final FraudDetectionService fraudDetectionService;
    private final FraudAlertRepository fraudAlertRepository;

    public FraudController(FraudDetectionService fraudDetectionService,
                          FraudAlertRepository fraudAlertRepository) {
        this.fraudDetectionService = fraudDetectionService;
        this.fraudAlertRepository = fraudAlertRepository;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(@RequestBody FraudDecisionRequest request) {
        FraudAlert alert = fraudDetectionService.evaluateTransaction(request);

        Map<String, Object> response = new HashMap<>();
        response.put("alertId", alert.getAlertId());
        response.put("transactionId", alert.getTransactionId());
        response.put("decision", alert.getDecision());
        response.put("riskScore", alert.getRiskScore());
        response.put("severity", alert.getSeverity());
        response.put("description", alert.getDescription());
        response.put("triggeredRules", alert.getTriggeredRules());
        response.put("modelScore", alert.getModelScore());
        response.put("rulesScore", alert.getRulesScore());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<FraudAlert>> getAlerts(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String decision) {

        List<FraudAlert> alerts;

        if (accountId != null) {
            alerts = fraudAlertRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        } else if (severity != null) {
            alerts = fraudAlertRepository.findBySeverityOrderByCreatedAtDesc(
                FraudSeverity.valueOf(severity.toUpperCase()));
        } else if (decision != null) {
            alerts = fraudAlertRepository.findByDecisionAndCreatedAtAfter(decision,
                java.time.LocalDateTime.now().minusDays(1));
        } else {
            alerts = fraudAlertRepository.findUnresolvedAlerts();
        }

        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<FraudAlert> getAlert(@PathVariable String alertId) {
        return fraudAlertRepository.findByAlertId(alertId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Fraud Detection Engine");
        return ResponseEntity.ok(response);
    }
}