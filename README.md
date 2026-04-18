# Real-time Fraud Detection Engine

Event-driven fraud detection microservice for banking transactions using Kafka, rule engine, and ML-based anomaly detection.

## Architecture

```
┌──────────────┐     ┌─────────────┐     ┌───────────────────┐
│  Banking     │────▶│   Kafka     │────▶│  Fraud Detection  │
│  Services    │     │  (transactions)    │  Engine           │
└──────────────┘     └─────────────┘     └─────────┬─────────┘
                                                    │
                          ┌──────────────────────────┼──────────────────────────┐
                          │                          │                          │
                          ▼                          ▼                          ▼
                ┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
                │  Rule Engine    │      │  ML Anomaly     │      │  Velocity       │
                │  (60% weight)   │      │  Detection      │      │  Check (Redis)  │
                │                 │      │  (40% weight)   │      │                 │
                └─────────────────┘      └─────────────────┘      └─────────────────┘
                          │                          │                          │
                          ▼                          ▼                          ▼
                ┌─────────────────────────────────────────────────────────────────┐
                │                    Decision Engine                             │
                │   BLOCK (≥0.9) | ALERT (≥0.6) | REVIEW (≥0.3) | APPROVE        │
                └─────────────────────────────────────────────────────────────────┘
                                                    │
                                                    ▼
                                        ┌─────────────────┐
                                        │  Fraud Alerts   │
                                        │  (Kafka + DB)   │
                                        └─────────────────┘
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Runtime | Java 17 + Spring Boot 3.2 |
| Messaging | Apache Kafka |
| Cache/Velocity | Redis |
| ML | Smile (anomaly detection) |
| Database | H2 (dev) / PostgreSQL (prod) |
| API Docs | SpringDoc OpenAPI |

## Features

- **Rule-based Detection** — Configurable rules with weighted scoring
- **ML Anomaly Detection** — Isolation Forest / Random Forest for unusual patterns
- **Real-time Velocity Checks** — Redis-based transaction frequency monitoring
- **Kafka Event-driven** — Consumes transaction events, produces fraud alerts
- **Multi-tier Decisions** — BLOCK, ALERT, REVIEW, APPROVE based on risk score
- **Historical Analysis** — Alert storage for audit and pattern analysis

## Rule Engine

| Rule ID | Rule | Weight |
|---------|------|--------|
| R001 | High Amount (>$5,000) | 0.4 |
| R002 | Blacklisted Country | 0.9 |
| R003 | High Velocity | 0.5 |
| R004 | Unknown Device | 0.3 |
| R005 | Online High Value | 0.35 |
| R006 | International ATM | 0.45 |

## Decision Thresholds

| Score | Decision | Action |
|-------|----------|--------|
| ≥ 0.9 | BLOCK | Decline transaction |
| ≥ 0.6 | ALERT | Notify fraud team |
| ≥ 0.3 | REVIEW | Queue for manual review |
| < 0.3 | APPROVE | Allow transaction |

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/fraud/evaluate` | Evaluate transaction for fraud |
| GET | `/api/v1/fraud/alerts` | List fraud alerts |
| GET | `/api/v1/fraud/alerts/{alertId}` | Get specific alert |
| GET | `/api/v1/fraud/health` | Health check |

### Evaluate Request

```json
{
  "transactionId": "TXN-123456",
  "accountId": "ACC-789",
  "amount": 5500.00,
  "currency": "USD",
  "merchant": "Amazon",
  "merchantCategory": "ECOMMERCE",
  "country": "US",
  "city": "New York",
  "channel": "ONLINE",
  "ipAddress": "192.168.1.1",
  "deviceId": "device-abc123",
  "cardLastFour": "4532"
}
```

### Evaluate Response

```json
{
  "alertId": "uuid-here",
  "transactionId": "TXN-123456",
  "decision": "ALERT",
  "riskScore": 0.6523,
  "severity": "MEDIUM",
  "description": "Combined risk score: 0.6523 (ML: 0.45, Rules: 0.60). Triggered rules: High Amount Transaction, Online High Value",
  "triggeredRules": "R001,R005",
  "modelScore": 0.4500,
  "rulesScore": 0.6000
}
```

## Kafka Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `transactions` | In (consume) | Incoming transactions from banking services |
| `fraud-alerts` | Out (produce) | Fraud decisions for downstream processing |

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Kafka (or Docker)
- Redis

### Run

```bash
# Build
./mvnw clean package -DskipTests

# Run
java -jar target/fraud-detection-service-1.0.0-SNAPSHOT.jar

# Or with Docker
cd docker && docker-compose up -d
```

### Test

```bash
# Evaluate a transaction
curl -X POST http://localhost:8081/api/v1/fraud/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-TEST-001",
    "accountId": "ACC-123",
    "amount": 6000,
    "currency": "USD",
    "merchant": "Test Merchant",
    "country": "US",
    "channel": "ONLINE"
  }'
```

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | localhost:9092 | Kafka bootstrap servers |
| `REDIS_HOST` | localhost | Redis host for velocity checks |
| `fraud.rules.high-amount-threshold` | 5000 | High amount threshold (USD) |
| `fraud.rules.velocity-window-seconds` | 300 | Velocity check window |
| `fraud.rules.max-transactions-per-window` | 10 | Max txns per window |
| `fraud.ml.anomaly-threshold` | 0.75 | ML anomaly threshold |
| `fraud.actions.block-threshold` | 0.9 | Block decision threshold |
| `fraud.actions.alert-threshold` | 0.6 | Alert threshold |

## Project Structure

```
fraud-detection-service/
├── src/main/java/com/banking/fraud/
│   ├── FraudDetectionApplication.java
│   ├── config/
│   ├── controller/
│   │   └── FraudController.java
│   ├── model/
│   │   ├── Transaction.java
│   │   ├── FraudAlert.java
│   │   └── FraudDecisionRequest.java
│   ├── repository/
│   │   └── FraudAlertRepository.java
│   ├── rule/
│   │   ├── RuleEngine.java
│   │   └── FraudRule.java
│   ├── ml/
│   │   └── AnomalyDetectionService.java
│   └── service/
│       ├── FraudDetectionService.java
│       ├── VelocityService.java
│       └── TransactionConsumerService.java
├── docker/
└── k8s/
```

## License

MIT License