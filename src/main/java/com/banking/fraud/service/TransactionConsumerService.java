package com.banking.fraud.service;

import com.banking.fraud.model.FraudDecisionRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class TransactionConsumerService {

    private final FraudDetectionService fraudDetectionService;

    public TransactionConsumerService(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @KafkaListener(topics = "${kafka.topics.transactions:transactions}",
                  groupId = "fraud-detection-group")
    public void consumeTransaction(FraudDecisionRequest transaction) {
        try {
            fraudDetectionService.evaluateTransaction(transaction);
        } catch (Exception e) {
            // Log and handle - in production, send to DLQ
            System.err.println("Error processing transaction: " + e.getMessage());
        }
    }
}