# Stream Sentinel — Implementation Guide

> Comprehensive technical reference for the Stream Sentinel anomaly detection engine.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Module Structure](#module-structure)
3. [Core Engine (`core-engine`)](#core-engine)
4. [Flink Job (`flink-job`)](#flink-job)
5. [Data Flow](#data-flow)
6. [Configuration Pipeline](#configuration-pipeline)
7. [Detection Engine Deep Dive](#detection-engine-deep-dive)
8. [Observability](#observability)
9. [Deployment](#deployment)
10. [Build System](#build-system)
11. [Testing Strategy](#testing-strategy)
12. [Extension Points](#extension-points)

---

## Architecture Overview

Stream Sentinel is a **cloud-native, real-time anomaly detection engine** built on Apache Flink and Apache Kafka. It follows a clean two-module Maven architecture that enforces separation of concerns:

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Stream Sentinel                              │
│                                                                      │
│  ┌─────────────────────────────┐  ┌──────────────────────────────┐  │
│  │       core-engine           │  │        flink-job              │  │
│  │  (zero Flink dependency)    │  │  (Flink streaming pipeline)  │  │
│  │                             │  │                              │  │
│  │  • Event / Alert models     │  │  • Kafka source/sink         │  │
│  │  • AnomalyDetector SPI     │──│  • Keyed process function    │  │
│  │  • 3 detector impls         │  │  • Ser/Deserialization       │  │
│  │  • YAML config loader       │  │  • Prometheus metrics        │  │
│  │  • Rule validation          │  │  • Health HTTP server        │  │
│  └─────────────────────────────┘  │  • Job configuration         │  │
│                                    └──────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

**Key design principle:** The `core-engine` module has **zero** Flink dependencies. All detection logic is pure Java, making it independently testable and reusable outside of Flink contexts.

---

## Module Structure

```
stream-sentinel/
├── pom.xml                          # Parent POM (dependency management, plugin versions)
├── core-engine/                     # Anomaly detection library (pure Java)
│   ├── pom.xml
│   └── src/
│       ├── main/java/.../core/
│       │   ├── model/               # Event, Alert, DetectionRule
│       │   ├── detection/           # AnomalyDetector, 3 implementations, DetectorFactory
│       │   └── config/              # RulesConfig, RulesLoader
│       └── test/java/.../core/
│           ├── detection/           # Detector + factory tests
│           └── config/              # RulesLoader tests
├── flink-job/                       # Flink streaming pipeline
│   ├── pom.xml                      # Shaded uber-JAR build
│   └── src/main/java/.../flink/
│       ├── StreamSentinelJob.java   # Main entry point
│       ├── AnomalyProcessFunction   # KeyedProcessFunction
│       ├── EventDeserializationSchema
│       ├── AlertSerializationSchema
│       ├── SentinelMetrics
│       ├── HealthServer
│       └── JobConfig
├── config/                          # Default YAML configurations
├── docker/                          # Multi-stage Dockerfile
├── k8s/                             # Kubernetes manifests
└── examples/                        # Sample data
```

---

## Core Engine

### Model Layer (`com.streamsentinel.core.model`)

#### `Event`
- **Purpose:** Generic event wrapper for free-form JSON from Kafka.
- **Design:** Uses Jackson's `@JsonAnySetter` / `@JsonAnyGetter` to store arbitrary fields in a `LinkedHashMap<String, Object>` — no rigid schema required.
- **Key methods:**
  - `getField(String)` → `Optional<Object>` — raw field lookup
  - `getNumericField(String)` → `Optional<Double>` — coerces `Number` and `String` types
  - `getStringField(String)` → `Optional<String>` — toString coercion
- **Immutability:** `getFields()` returns `Collections.unmodifiableMap()`
- **Thread safety:** Not thread-safe; relies on Flink's single-threaded operator model.

#### `Alert`
- **Purpose:** Emitted when an anomaly detection rule fires. Serialized to JSON for Kafka.
- **Construction:** Builder pattern with required `ruleName` + `timestamp` (enforced via `Objects.requireNonNull`).
- **Defensive copying:** `originalEvent` map is copied in both constructor and setter to prevent mutation.
- **Equality:** Based on `(ruleName, key, timestamp)` — intentionally excludes `details` and `originalEvent`.

#### `DetectionRule`
- **Purpose:** POJO deserialized from YAML configuration.
- **Validation:** `validate()` method with type-specific checks:
  - `rate` → requires `keyField`, `windowSeconds > 0`, `threshold > 0`
  - `threshold` → requires `field`
  - `statistical` → requires `field`, `windowSize >= 2`, `deviationFactor > 0`
- **Type normalization:** `setType()` lowercases the input for case-insensitive matching.

### Detection Layer (`com.streamsentinel.core.detection`)

#### `AnomalyDetector` (Interface)
- **Contract:** `Optional<Alert> evaluate(Event event)` + `String getRuleName()`
- **Serializable:** Required by Flink's checkpointing (detectors live in keyed state).
- **Stateful:** Implementations accumulate per-key observations across calls.

#### `ThresholdDetector`
- **Type:** Stateless. Each event evaluated independently.
- **Logic:** Fires when `event.getNumericField(field) > threshold`.
- **Edge case:** Missing or non-numeric fields silently skipped (logged at TRACE).

#### `RateSpikeDetector`
- **Type:** Stateful. Maintains `Deque<Long>` of event timestamps.
- **Logic:** Sliding window eviction + count check:
  1. Evict timestamps older than `now - windowSeconds`
  2. Add current timestamp
  3. Fire if `count > threshold`
- **Time source:** Uses `event.getIngestionTime()`, falls back to `System.currentTimeMillis()`.

#### `StatisticalOutlierDetector`
- **Type:** Stateful. Maintains `Deque<Double>` of recent values.
- **Logic:** Moving average + standard deviation:
  1. Compute mean and σ from the window
  2. Fire if `|value - mean| > deviationFactor × σ`
  3. Special case: if σ = 0 (all identical), any deviation fires
- **Warm-up:** No firing until ≥ 2 observations (`MIN_HISTORY_SIZE`).
- **Ordering:** Value is checked *before* being added to the window (prevents self-influence).

#### `DetectorFactory`
- **Pattern:** Static factory with switch expression over rule type.
- **Extension point:** Add new types by adding a `case` in the `create()` method.
- **Batch creation:** `createAll()` maps a list of rules to detectors.

### Config Layer (`com.streamsentinel.core.config`)

#### `RulesConfig`
- **Purpose:** Top-level YAML POJO (`rules:` list).
- **Validation:** Delegates to each `DetectionRule.validate()`, collects all errors, and throws a single aggregated `IllegalStateException`.
- **Immutability:** `getRules()` returns `Collections.unmodifiableList()`.

#### `RulesLoader`
- **Resolution order:**
  1. `RULES_CONFIG_PATH` environment variable (file system)
  2. Explicit `fromFile(String)` call
  3. Classpath `rules.yml` fallback
- **Fail-fast:** All `load*` methods call `config.validate()` immediately after parsing.
- **YAML engine:** SnakeYAML with `allowDuplicateKeys(false)`.

---

## Flink Job

### `StreamSentinelJob` (Entry Point)
- **Pipeline:** Kafka source → null filter → keyBy(configurable field) → `AnomalyProcessFunction` → Kafka sink
- **Checkpointing:** EXACTLY_ONCE mode with configurable interval, 1 concurrent max, retained on cancellation.
- **Health server:** Started before pipeline execution, with JVM shutdown hook for graceful stop.

### `AnomalyProcessFunction` (Core Processing)
- **Type:** `KeyedProcessFunction<String, Event, Alert>` — runs all detectors per key.
- **State management:**
  - `ValueState<List<AnomalyDetector>>` — lazily initialized per key on first event.
  - Detectors are created via `DetectorFactory.createAll(rules)` and persisted in Flink's checkpoint state.
- **Processing flow:**
  1. Initialize detectors if this is a new key
  2. Run every detector's `evaluate()` against the event
  3. Enrich fired alerts with the Flink keyed-stream key
  4. Update state (detectors may have mutated internal accumulators)
  5. Record metrics (events processed, anomalies, latency)
- **Error resilience:** Individual detector exceptions are caught and logged; processing continues with the next detector.

### `EventDeserializationSchema`
- **Converts:** Raw Kafka `byte[]` → `Event` via Jackson `ObjectMapper`.
- **Fault tolerance:** Malformed messages return `null` (logged at WARN) — dropped by the `.filter(Objects::nonNull)` in the pipeline.
- **ObjectMapper:** Lazily initialized (`transient`), registers `JavaTimeModule`, ignores unknown properties.

### `AlertSerializationSchema`
- **Converts:** `Alert` → JSON `byte[]` via Jackson.
- **Timestamps:** `WRITE_DATES_AS_TIMESTAMPS = false` → ISO-8601 strings.
- **Failure handling:** Returns empty `byte[0]` on error (logged at ERROR).

### `SentinelMetrics`
- **Custom Flink metrics** in the `stream_sentinel` group:
  - `events_processed_total` (Counter)
  - `anomalies_detected_total` (Counter)
  - `processing_latency_ms` (Histogram — `DescriptiveStatisticsHistogram` with 350-sample window)
- **Exposure:** Via Flink's configured metric reporters (e.g., Prometheus).

### `HealthServer`
- **Lightweight HTTP** using JDK's built-in `com.sun.net.httpserver.HttpServer`.
- **Endpoints:** `/health` and `/readiness` both return `200 {"status":"UP"}`.
- **Threading:** Single daemon thread executor.
- **Lifecycle:** `AtomicBoolean` guard ensures `stop()` is idempotent.

### `JobConfig`
- **Immutable** configuration object resolved from environment variables.
- **Builder pattern** with validation at `build()` time (range checks, non-blank assertions).
- **Factory:** `fromEnvironment()` reads all env vars with sensible defaults.
- **Kafka helpers:** `kafkaConsumerProperties()` and `kafkaProducerProperties()` produce ready-to-use `Properties` objects.

---

## Data Flow

```
                                        Stream Sentinel Pipeline
                                        ─────────────────────────

  ┌────────────┐     ┌─────────────────────────┐     ┌────────────────────────────┐     ┌────────────┐
  │            │     │  EventDeserialization     │     │  AnomalyProcessFunction    │     │            │
  │  Kafka     │────►│  Schema                  │────►│                            │────►│  Kafka     │
  │  (events)  │     │                          │     │  For each key:             │     │  (alerts)  │
  │            │     │  • JSON byte[] → Event   │     │  • Init detectors (lazy)   │     │            │
  └────────────┘     │  • Set ingestion time    │     │  • Run all detectors       │     └────────────┘
                     │  • Drop malformed (null) │     │  • Emit alerts             │
                     └─────────────────────────┘     │  • Update keyed state      │
                                                      │  • Record metrics          │
                                                      └────────────────────────────┘
                                                                │
                                                                ▼
                                                      ┌────────────────────┐
                                                      │  AlertSerialization │
                                                      │  Schema             │
                                                      │  • Alert → JSON    │
                                                      │  • ISO-8601 dates  │
                                                      └────────────────────┘
```

**Key-by strategy:** Events are keyed by a configurable field (`DEFAULT_KEY_FIELD`, default: `userId`). Each unique key value gets its own isolated set of detector instances in Flink state.

---

## Configuration Pipeline

```
Environment Variables          YAML File                   Code Validation
─────────────────────         ──────────                   ───────────────

KAFKA_BOOTSTRAP_SERVERS  ──►  JobConfig.fromEnvironment()
KAFKA_INPUT_TOPIC        ──►  Builder validates all fields
RULES_CONFIG_PATH        ──►  ───────────────────────────────►  RulesLoader
                                                                    │
                              config/rules.yml  ─────────────►  SnakeYAML parse
                                                                    │
                                                                    ▼
                                                              RulesConfig.validate()
                                                                    │
                                                              DetectionRule.validate() × N
                                                                    │
                                                              DetectorFactory.createAll()
```

**Fail-fast guarantee:** Any misconfiguration (invalid env vars, malformed YAML, invalid rule parameters) throws before the pipeline starts.

---

## Detection Engine Deep Dive

### Detector Lifecycle

1. **Loading:** YAML → `DetectionRule` (via SnakeYAML)
2. **Validation:** `DetectionRule.validate()` (type-specific checks)
3. **Instantiation:** `DetectorFactory.create(rule)` → detector instance
4. **Per-key state:** Each Flink key gets its own detector instances via `ValueState<List<AnomalyDetector>>`
5. **Evaluation:** `detector.evaluate(event)` → `Optional<Alert>`
6. **Checkpointing:** Detectors are `Serializable`; Flink snapshots them during checkpoints

### Detection Algorithm Summary

| Detector | State | Algorithm | Complexity |
|----------|-------|-----------|------------|
| `ThresholdDetector` | None | `value > threshold` | O(1) |
| `RateSpikeDetector` | `Deque<Long>` timestamps | Sliding window eviction + count | O(W) eviction |
| `StatisticalOutlierDetector` | `Deque<Double>` values | Moving average + σ deviation | O(W) computation |

*W = window size*

---

## Observability

### Prometheus Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `stream_sentinel_events_processed_total` | Counter | Total events evaluated |
| `stream_sentinel_anomalies_detected_total` | Counter | Total alerts fired |
| `stream_sentinel_processing_latency_ms` | Histogram | Per-event processing time (p50/p95/p99) |

### Health Endpoints

| Endpoint | Purpose | Response |
|----------|---------|----------|
| `GET /health` | K8s liveness probe | `200 {"status":"UP"}` |
| `GET /readiness` | K8s readiness probe | `200 {"status":"UP"}` |

---

## Deployment

### Docker

Multi-stage build:
1. **Build stage:** `maven:3.9-eclipse-temurin-17` — compiles and packages fat JAR
2. **Runtime stage:** `flink:1.18.1-java17` — copies JAR to `/opt/flink/usrlib/`

Dependency caching: POM files copied first, `mvn dependency:go-offline` cached layer.

### Kubernetes

| Manifest | Purpose |
|----------|---------|
| `namespace.yml` | Dedicated `stream-sentinel` namespace |
| `configmap.yml` | Detection rules YAML mounted as ConfigMap |
| `deployment.yml` | Flink TaskManager with resource limits, probes, env vars |
| `service.yml` | ClusterIP service exposing health port |
| `hpa.yml` | Horizontal Pod Autoscaler with CPU/memory targets |

---

## Build System

### Parent POM
- **Java 17**, UTF-8 encoding
- **Dependency management** for all external libraries (Flink 1.18.1, Kafka 3.6.1, Jackson 2.16.1, etc.)
- **Plugin management** for compiler, surefire, shade, jar plugins

### `core-engine` POM
- Dependencies: Jackson, SnakeYAML, SLF4J, JUnit 5, AssertJ
- No Flink dependencies

### `flink-job` POM
- Dependencies: `core-engine`, Flink (provided), Kafka connector, Prometheus, Jackson, Logback
- **maven-shade-plugin:** Builds fat JAR, excludes Flink core JARs (provided by cluster), merges SPI `META-INF/services`

---

## Testing Strategy

### Current Test Coverage

| Test Class | Module | What It Tests |
|------------|--------|---------------|
| `ThresholdDetectorTest` | core-engine | Fire/no-fire, exact threshold, missing field, string numbers |
| `RateSpikeDetectorTest` | core-engine | Below threshold, exceed threshold, window eviction |
| `StatisticalOutlierDetectorTest` | core-engine | Insufficient data, normal values, extreme outlier, zero-σ |
| `DetectorFactoryTest` | core-engine | Correct instantiation per type, unknown type rejection |
| `RulesLoaderTest` | core-engine | Classpath YAML loading, missing resource handling |

### Testing Tools
- **JUnit 5** (`junit-jupiter`) — test framework
- **AssertJ** (`assertj-core`) — fluent assertions
- **Logback** — test-scoped logging backend

### Run Tests
```bash
mvn clean test
```

---

## Extension Points

### Adding a New Detector Type

1. **Implement** `AnomalyDetector` in `core-engine`:
   ```java
   public class MyDetector implements AnomalyDetector {
       @Override
       public Optional<Alert> evaluate(Event event) { /* ... */ }
       @Override
       public String getRuleName() { return ruleName; }
   }
   ```

2. **Register** in `DetectorFactory.create()`:
   ```java
   case "custom" -> new MyDetector(rule);
   ```

3. **Add validation** in `DetectionRule.validate()`:
   ```java
   case "custom" -> { /* validate custom-specific fields */ }
   ```

4. **Configure** in `rules.yml`:
   ```yaml
   - name: my_rule
     type: custom
     field: myField
     threshold: 42
   ```

### Adding New Metrics

Register in `SentinelMetrics` constructor:
```java
MetricGroup sentinelGroup = metricGroup.addGroup("stream_sentinel");
Counter myCounter = sentinelGroup.counter("my_metric_total");
```

### Adding New Health Endpoints

Register in `HealthServer.start()`:
```java
server.createContext("/my-endpoint", MyHandler::handle);
```
