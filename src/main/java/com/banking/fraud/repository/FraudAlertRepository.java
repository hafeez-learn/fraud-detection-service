package com.banking.fraud.repository;

import com.banking.fraud.model.FraudAlert;
import com.banking.fraud.model.FraudSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    Optional<FraudAlert> findByAlertId(String alertId);

    Optional<FraudAlert> findByTransactionId(String transactionId);

    List<FraudAlert> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<FraudAlert> findBySeverityOrderByCreatedAtDesc(FraudSeverity severity);

    List<FraudAlert> findByDecisionAndCreatedAtAfter(String decision, LocalDateTime after);

    @Query("SELECT f FROM FraudAlert f WHERE f.resolvedAt IS NULL ORDER BY f.createdAt DESC")
    List<FraudAlert> findUnresolvedAlerts();

    @Query("SELECT COUNT(f) FROM FraudAlert f WHERE f.decision = 'BLOCK' AND f.createdAt > ?1")
    long countBlockedAfter(LocalDateTime since);

    @Query("SELECT AVG(f.riskScore) FROM FraudAlert f WHERE f.accountId = ?1 AND f.createdAt > ?2")
    Double averageRiskScoreForAccount(String accountId, LocalDateTime since);
}