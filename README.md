# Stream Sentinel

**Production-ready, cloud-native anomaly detection and alert engine for real-time event streams.**

Stream Sentinel consumes events from Apache Kafka, runs configurable anomaly-detection rules via Apache Flink, and publishes alerts back to Kafka — all deployable on Kubernetes with Prometheus-compatible observability.

---

## Architecture

```
┌─────────────┐        ┌────────────────────────────────────────────────────┐        ┌──────────────┐
│             │        │                  Stream Sentinel                  │        │              │
│   Kafka     │        │  ┌───────────┐   ┌────────────────┐              │        │    Kafka     │
│  (events)   ├───────►│  │  Kafka    │──►│  Keyed Process │──► Alerts ───┼───────►│   (alerts)   │
│             │        │  │  Source   │   │   Function     │              │        │              │
└─────────────┘        │  └───────────┘   │                │              │        └──────────────┘
                       │                  │ ┌────────────┐ │              │
                       │                  │ │ Rate Spike │ │              │        ┌──────────────┐
                       │                  │ │ Detector   │ │              │        │  Prometheus  │
                       │                  │ ├────────────┤ │   Metrics ──┼───────►│  / Grafana   │
                       │                  │ │ Threshold  │ │              │        └──────────────┘
                       │                  │ │ Detector   │ │              │
                       │                  │ ├────────────┤ │              │        ┌──────────────┐
                       │                  │ │ Statistical│ │              │        │  Kubernetes  │
                       │                  │ │ Outlier    │ │   Health ───┼───────►│  Probes      │
                       │                  │ └────────────┘ │              │        └──────────────┘
                       │                  └────────────────┘              │
                       └────────────────────────────────────────────────────┘
```

---

## Features

| Feature | Description |
|---------|-------------|
| **Rate spike detection** | Alerts when events per key exceed a threshold within a time window |
| **Threshold detection** | Alerts when a numeric field exceeds a static limit |
| **Statistical outlier detection** | Alerts when a value deviates from a moving average |
| **Pluggable architecture** | Add new rule types by implementing a single interface |
| **Dynamic configuration** | Rules loaded from YAML; configurable via env vars and ConfigMaps |
| **Exactly-once processing** | Flink checkpointing with at-least-once / exactly-once semantics |
| **Prometheus metrics** | `events_processed_total`, `anomalies_detected_total`, `processing_latency_ms` |
| **Health endpoint** | `/health` and `/readiness` for Kubernetes probes |
| **Kubernetes-native** | Full K8s manifests with HPA, ConfigMap, and resource limits |
| **Docker support** | Multi-stage Dockerfile with dependency caching |

---

## Project Structure

```
stream-sentinel/
├── core-engine/                    # Anomaly detection library
│   └── src/main/java/
│       └── com/streamsentinel/core/
│           ├── model/              # Event, Alert, DetectionRule
│           ├── detection/          # AnomalyDetector, Rate, Threshold, Statistical
│           └── config/             # RulesConfig, RulesLoader
├── flink-job/                      # Flink streaming pipeline
│   └── src/main/java/
│       └── com/streamsentinel/flink/
│           ├── StreamSentinelJob.java
│           ├── AnomalyProcessFunction.java
│           ├── EventDeserializationSchema.java
│           ├── AlertSerializationSchema.java
│           ├── SentinelMetrics.java
│           ├── HealthServer.java
│           └── JobConfig.java
├── config/
│   ├── application.yml
│   └── rules.yml
├── docker/
│   ├── Dockerfile
│   └── .dockerignore
├── k8s/
│   ├── namespace.yml
│   ├── configmap.yml
│   ├── deployment.yml
│   ├── service.yml
│   └── hpa.yml
├── examples/
│   ├── sample-events.json
│   └── sample-rules.yml
├── pom.xml                         # Parent POM
└── README.md
```

---

## Prerequisites

- **Java 17+** (JDK)
- **Maven 3.8+**
- **Apache Kafka** (running cluster or Docker Compose)
- **Apache Flink 1.18** (cluster for production, or local for development)
- **Docker** (for container builds)
- **kubectl** (for Kubernetes deployment)

---

## Quick Start

### 1. Build

```bash
mvn clean package
```

This produces a fat JAR at `flink-job/target/flink-job-1.0.0-SNAPSHOT.jar`.

### 2. Run Locally (requires Kafka + Flink)

```bash
# Set environment variables
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_INPUT_TOPIC=events
export KAFKA_ALERT_TOPIC=alerts
export RULES_CONFIG_PATH=config/rules.yml

# Submit to a local Flink cluster
flink run flink-job/target/flink-job-1.0.0-SNAPSHOT.jar
```

### 3. Produce Sample Events

```bash
cat examples/sample-events.json | jq -c '.[]' | \
  kafka-console-producer --bootstrap-server localhost:9092 --topic events
```

### 4. Consume Alerts

```bash
kafka-console-consumer --bootstrap-server localhost:9092 --topic alerts --from-beginning
```

---

## Configuration

### Environment Variables

All configuration is driven by environment variables (ideal for containers):

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `KAFKA_INPUT_TOPIC` | `events` | Topic to consume events from |
| `KAFKA_ALERT_TOPIC` | `alerts` | Topic to publish alerts to |
| `KAFKA_GROUP_ID` | `stream-sentinel` | Kafka consumer group ID |
| `FLINK_PARALLELISM` | `1` | Flink job parallelism |
| `FLINK_CHECKPOINT_INTERVAL_MS` | `60000` | Checkpoint interval (ms) |
| `RULES_CONFIG_PATH` | *(classpath)* | Path to external `rules.yml` |
| `HEALTH_PORT` | `8080` | HTTP health endpoint port |
| `DEFAULT_KEY_FIELD` | `userId` | JSON field used for key-by partitioning |

### Detection Rules

Rules are defined in YAML (see `config/rules.yml`):

```yaml
rules:
  - name: high_rate
    type: rate
    keyField: userId
    windowSeconds: 10
    threshold: 5

  - name: high_amount
    type: threshold
    field: amount
    threshold: 10000

  - name: unusual_amount
    type: statistical
    field: amount
    windowSize: 20
    deviationFactor: 2.5
    threshold: 0
```

#### Rule Types

| Type | Parameters | Description |
|------|-----------|-------------|
| `rate` | `keyField`, `windowSeconds`, `threshold` | Events per key exceeding threshold within window |
| `threshold` | `field`, `threshold` | Numeric field value exceeding threshold |
| `statistical` | `field`, `windowSize`, `deviationFactor` | Value deviating from moving average by N × σ |

---

## Alert Format

Alerts are published as JSON to the configured Kafka topic:

```json
{
  "ruleName": "high_amount",
  "key": "user_002",
  "timestamp": "2024-01-15T10:01:00Z",
  "details": "Threshold exceeded: amount=15000.00 (threshold: 10000.00)",
  "originalEvent": {
    "userId": "user_002",
    "amount": 15000.00,
    "merchantId": "merch_99",
    "category": "luxury"
  }
}
```

---

## Docker

### Build Image

```bash
docker build -f docker/Dockerfile -t stream-sentinel:latest .
```

### Run Standalone

```bash
docker run -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
           stream-sentinel:latest \
           standalone-job \
           --job-classname com.streamsentinel.flink.StreamSentinelJob
```

---

## Kubernetes Deployment

### 1. Create Namespace

```bash
kubectl apply -f k8s/namespace.yml
```

### 2. Deploy ConfigMap (rules)

```bash
kubectl apply -f k8s/configmap.yml
```

### 3. Deploy the Job

```bash
kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
kubectl apply -f k8s/hpa.yml
```

### 4. Verify

```bash
kubectl -n stream-sentinel get pods
kubectl -n stream-sentinel logs -f deployment/stream-sentinel
```

### Validate Manifests (dry-run)

```bash
kubectl apply --dry-run=client -f k8s/
```

---

## Observability

### Prometheus Metrics

Stream Sentinel exposes custom Flink metrics via the Prometheus metric reporter:

| Metric | Type | Description |
|--------|------|-------------|
| `stream_sentinel_events_processed_total` | Counter | Total events evaluated |
| `stream_sentinel_anomalies_detected_total` | Counter | Total alerts fired |
| `stream_sentinel_processing_latency_ms` | Histogram | Per-event processing time (ms) |

Enable the Prometheus reporter in `flink-conf.yaml`:

```yaml
metrics.reporter.prom.factory.class: org.apache.flink.metrics.prometheus.PrometheusReporterFactory
metrics.reporter.prom.port: 9249
```

### Health Endpoints

| Endpoint | Use |
|----------|-----|
| `GET /health` | Liveness probe |
| `GET /readiness` | Readiness probe |

---

## Extending Stream Sentinel

To add a new detection rule type:

1. Create a class implementing `AnomalyDetector` in `core-engine/`:

```java
public class MyCustomDetector implements AnomalyDetector {
    @Override
    public Optional<Alert> evaluate(Event event) { /* ... */ }

    @Override
    public String getRuleName() { return ruleName; }
}
```

2. Register it in `DetectorFactory`:

```java
case "custom" -> new MyCustomDetector(rule);
```

3. Add a rule in `rules.yml`:

```yaml
- name: my_custom_rule
  type: custom
  field: myField
  threshold: 42
```

---

## Running Tests

```bash
mvn test
```

The test suite covers:
- `ThresholdDetector` – fire / no-fire / edge cases
- `RateSpikeDetector` – burst detection and window eviction
- `StatisticalOutlierDetector` – outlier detection and insufficient-data handling
- `DetectorFactory` – correct instantiation per type
- `RulesLoader` – YAML parsing and error handling

---

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** the repository
2. Create a **feature branch** (`git checkout -b feature/my-detector`)
3. **Write tests** for new functionality
4. Ensure `mvn test` passes
5. Open a **Pull Request** with a clear description

### Code Style

- Java 17 features (records, switch expressions, pattern matching)
- Clean architecture: `core-engine` has zero Flink dependency
- All configuration via environment variables
- No hardcoded values
- Javadoc on public APIs

---

## License

This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

---

## Acknowledgements

Built with:
- [Apache Flink](https://flink.apache.org/)
- [Apache Kafka](https://kafka.apache.org/)
- [Prometheus](https://prometheus.io/)
- [Jackson](https://github.com/FasterXML/jackson)
- [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml)
