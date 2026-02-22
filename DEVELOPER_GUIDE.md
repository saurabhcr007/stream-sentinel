# Stream Sentinel — Developer Deep-Dive Guide

> A complete technical walkthrough of Stream Sentinel's architecture, every component, every design decision, and the code behind it all.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Project Structure & Build System](#2-project-structure--build-system)
3. [Module 1: core-engine — The Detection Library](#3-module-1-core-engine--the-detection-library)
   - [3.1 Data Models](#31-data-models-the-language-of-stream-sentinel)
   - [3.2 The AnomalyDetector Interface](#32-the-anomalydetector-interface)
   - [3.3 ThresholdDetector](#33-thresholddetector--stateless-field-comparison)
   - [3.4 RateSpikeDetector](#34-ratespikedetector--sliding-window-counting)
   - [3.5 StatisticalOutlierDetector](#35-statisticaloutlierdetector--moving-average--standard-deviation)
   - [3.6 DetectorFactory](#36-detectorfactory--the-single-extension-point)
   - [3.7 Configuration Loading](#37-configuration-loading-rulesconfig--rulesloader)
4. [Module 2: flink-job — The Streaming Pipeline](#4-module-2-flink-job--the-streaming-pipeline)
   - [4.1 StreamSentinelJob (Main Entry Point)](#41-streamsentineljob--the-main-entry-point)
   - [4.2 JobConfig](#42-jobconfig--environment-driven-configuration)
   - [4.3 EventDeserializationSchema](#43-eventdeserializationschema--kafka-bytes-to-event)
   - [4.4 AnomalyProcessFunction](#44-anomalyprocessfunction--the-heart-of-the-pipeline)
   - [4.5 AlertSerializationSchema](#45-alertserializationschema--alert-to-kafka-bytes)
   - [4.6 SentinelMetrics](#46-sentinelmetrics--observability)
   - [4.7 HealthServer](#47-healthserver--kubernetes-probes)
5. [Technology Stack & Why Each Was Chosen](#5-technology-stack--why-each-was-chosen)
6. [Data Flow: End-to-End](#6-data-flow-end-to-end)
7. [How to Extend Stream Sentinel](#7-how-to-extend-stream-sentinel)
8. [Testing Strategy](#8-testing-strategy)
9. [Deployment & Infrastructure](#9-deployment--infrastructure)

---

## 1. Architecture Overview

Stream Sentinel follows a **clean separation** between detection logic and streaming infrastructure:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Stream Sentinel                            │
│                                                                     │
│  ┌───────────────────────────────┐   ┌────────────────────────────┐ │
│  │       core-engine             │   │        flink-job           │ │
│  │  (pure Java, zero Flink dep)  │   │  (Flink-specific glue)    │ │
│  │                               │   │                            │ │
│  │  • Event / Alert / Rule       │   │  • StreamSentinelJob       │ │
│  │  • AnomalyDetector interface  │◄──┤  • AnomalyProcessFunction │ │
│  │  • 3 built-in detectors       │   │  • Kafka Source/Sink       │ │
│  │  • RulesLoader (YAML)         │   │  • Metrics & Health        │ │
│  │  • DetectorFactory            │   │  • JobConfig               │ │
│  └───────────────────────────────┘   └────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

**Why two modules?**

- `core-engine` has **zero Flink dependency**. It can be unit-tested, reused in a Spring Boot app, or embedded in any Java program.
- `flink-job` depends on `core-engine` and adds the Flink streaming glue: Kafka connectors, keyed state, checkpointing, metrics.

This means the detection logic is **portable and testable** without standing up a Flink cluster.

---

## 2. Project Structure & Build System

### Maven Multi-Module Project

```
stream-sentinel/
├── pom.xml                    ← Parent POM (dependency management)
├── core-engine/
│   ├── pom.xml                ← Detection library
│   └── src/
│       ├── main/java/com/streamsentinel/core/
│       │   ├── model/         ← Event, Alert, DetectionRule
│       │   ├── detection/     ← AnomalyDetector + 3 implementations
│       │   └── config/        ← RulesConfig, RulesLoader
│       └── test/java/         ← 18 unit tests
├── flink-job/
│   ├── pom.xml                ← Flink pipeline (shaded fat JAR)
│   └── src/main/java/com/streamsentinel/flink/
│       ├── StreamSentinelJob.java
│       ├── AnomalyProcessFunction.java
│       ├── EventDeserializationSchema.java
│       ├── AlertSerializationSchema.java
│       ├── SentinelMetrics.java
│       ├── HealthServer.java
│       └── JobConfig.java
├── config/                    ← Default YAML configuration
├── docker/                    ← Multi-stage Dockerfile
└── k8s/                       ← Kubernetes manifests
```

### Why Maven?

Maven was chosen because:
- Apache Flink officially publishes Maven artifacts and its documentation uses Maven examples
- Multi-module support (`core-engine` + `flink-job`) is first-class
- The `maven-shade-plugin` produces the required "fat JAR" (all dependencies bundled) that Flink expects

### Key Dependencies from `pom.xml`

```xml
<!-- Defined in parent pom.xml <properties> -->
<flink.version>1.18.1</flink.version>      <!-- Stream processing engine -->
<kafka.version>3.6.1</kafka.version>       <!-- Event broker client -->
<jackson.version>2.16.1</jackson.version>  <!-- JSON serialization -->
<snakeyaml.version>2.2</snakeyaml.version> <!-- YAML rule parsing -->
<slf4j.version>2.0.11</slf4j.version>      <!-- Logging facade -->
<logback.version>1.4.14</logback.version>  <!-- Logging implementation -->
<prometheus.version>0.16.0</prometheus.version> <!-- Metrics export -->
<junit.version>5.10.1</junit.version>      <!-- Unit testing -->
<assertj.version>3.25.1</assertj.version>  <!-- Fluent test assertions -->
```

**Note:** Flink dependencies are `<scope>provided</scope>` because the Flink runtime provides them when the job is submitted to a cluster.

---

## 3. Module 1: core-engine — The Detection Library

The `core-engine` module contains all anomaly detection logic with **zero dependency on Apache Flink**. This deliberate separation means:

- Detectors can be **unit-tested without a Flink cluster**
- The library could be **reused** in a Spring Boot microservice, AWS Lambda, or any Java application
- Clean architecture: detection _policy_ is decoupled from streaming _infrastructure_

---

### 3.1 Data Models: The Language of Stream Sentinel

#### `Event.java` — The Input

**What:** A generic, schema-free wrapper for JSON events arriving from Kafka.

**Why schema-free?** Stream Sentinel needs to process events from any domain (payments, IoT, logs) without requiring a rigid schema. Jackson's `@JsonAnySetter` / `@JsonAnyGetter` dynamically maps every JSON key-value pair into a `Map<String, Object>`.

**Key code:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event implements Serializable {

    // Every key-value pair from the JSON event is stored here
    private final Map<String, Object> fields = new LinkedHashMap<>();

    // Jackson calls this for every JSON property during deserialization
    @JsonAnySetter
    public void setField(String key, Object value) {
        Objects.requireNonNull(key, "Field key must not be null");
        fields.put(key, value);
    }

    // Returns an UNMODIFIABLE view — prevents accidental mutation
    @JsonAnyGetter
    public Map<String, Object> getFields() {
        return Collections.unmodifiableMap(fields);
    }
```

**Why `LinkedHashMap`?** Preserves the insertion order of JSON fields, so serialized output matches the input order.

**Why `Serializable`?** Flink serializes objects when checkpointing state. Events must be serializable to survive Flink task manager restarts.

**Numeric field extraction** handles both `Number` types (Jackson's default for JSON numbers) and string-encoded numbers:

```java
public Optional<Double> getNumericField(String fieldName) {
    Object raw = fields.get(fieldName);
    if (raw instanceof Number n) {          // Java 17 pattern matching
        return Optional.of(n.doubleValue());
    }
    if (raw instanceof String s) {          // Handle "123.45" strings
        try {
            return Optional.of(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
    return Optional.empty();
}
```

**Why `Optional<Double>` instead of returning 0 or throwing?** The detector must distinguish between "field isn't present" and "field value is 0". Returning `Optional.empty()` allows detectors to skip events that don't have the target field.

---

#### `Alert.java` — The Output

**What:** The structured output emitted when a detection rule fires.

**Why a Builder pattern?** Alerts have required fields (`ruleName`, `timestamp`) and optional fields (`key`, `details`, `originalEvent`). The Builder enforces null-checks at construction time:

```java
private Alert(Builder builder) {
    this.ruleName = Objects.requireNonNull(builder.ruleName, "ruleName must not be null");
    this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
    this.key = builder.key;
    this.details = builder.details;
    // DEFENSIVE COPY — prevents callers from mutating the event after alert creation
    this.originalEvent = builder.originalEvent != null
            ? new LinkedHashMap<>(builder.originalEvent)
            : null;
}
```

**Why defensive copies?** The `originalEvent` is a map taken from the `Event` object. Without a defensive copy, any later mutation of the original Event would silently change the Alert — a dangerous bug in a concurrent streaming system.

**Usage:**

```java
Alert.builder()
    .ruleName("high_amount")
    .key("user_002")
    .timestamp(Instant.now())
    .details("Threshold exceeded: amount=15000.00 (threshold: 10000.00)")
    .originalEvent(event.getFields())
    .build();
```

---

#### `DetectionRule.java` — The Configuration

**What:** A POJO representing a single detection rule loaded from YAML.

**Key design decisions:**

1. **Type normalization:** The setter normalizes the type to lowercase so `"Rate"`, `"RATE"`, and `"rate"` all work:

```java
public void setType(String type) {
    this.type = type != null ? type.toLowerCase(Locale.ROOT) : null;
}
```

2. **Comprehensive validation:** The `validate()` method checks type-specific constraints:

```java
public void validate() {
    List<String> errors = new ArrayList<>();

    if (name == null || name.isBlank()) errors.add("Rule 'name' is required");
    if (type == null || type.isBlank()) errors.add("Rule 'type' is required");

    if (type != null) {
        switch (type.toLowerCase(Locale.ROOT)) {
            case "rate" -> {
                if (keyField == null || keyField.isBlank())
                    errors.add("Rate rule '" + name + "' requires 'keyField'");
                if (windowSeconds <= 0)
                    errors.add("Rate rule '" + name + "' requires 'windowSeconds' > 0");
                if (threshold <= 0)
                    errors.add("Rate rule '" + name + "' requires 'threshold' > 0");
            }
            // ... threshold and statistical cases ...
            default -> errors.add("Unknown rule type: '" + type + "'");
        }
    }

    if (!errors.isEmpty()) {
        throw new IllegalStateException(
            "Invalid DetectionRule: " + String.join("; ", errors));
    }
}
```

**Why collect all errors before throwing?** If a rule has multiple problems, the user sees them all at once instead of fixing them one-by-one. This is a **fail-fast** approach that improves developer experience.

---

### 3.2 The AnomalyDetector Interface

```java
public interface AnomalyDetector extends Serializable {

    /**
     * Evaluate a single event and decide whether it constitutes an anomaly.
     *
     * @return an Alert if the event triggers the rule, empty otherwise
     */
    Optional<Alert> evaluate(Event event);

    /**
     * Return the unique name of the rule this detector enforces.
     */
    String getRuleName();
}
```

**Why `extends Serializable`?** Flink stores one detector instance per key in its **managed keyed state**. When Flink takes a checkpoint, it serializes all state (including detectors) to persistent storage. If a detector isn't Serializable, checkpointing fails.

**Why `Optional<Alert>` instead of `null`?** The Java `Optional` type:
- Makes the "no alert" case explicit in the type signature
- Prevents `NullPointerException` bugs when callers forget to check for null
- Enables fluent chaining: `detector.evaluate(event).ifPresent(out::collect)`

**Design contract:** Detectors are **stateful** — each instance is bound to a single key (e.g., `userId`) and accumulates observations across events. This is critical for rate counting and statistical analysis.

---

### 3.3 ThresholdDetector — Stateless Field Comparison

**What it does:** Fires when a numeric field value exceeds a configured threshold.

**When to use:** Absolute limits — transaction amounts, temperature readings, resource utilization.

**This detector is stateless:** Each event is evaluated independently. No history is maintained.

```java
public class ThresholdDetector implements AnomalyDetector {
    private final String ruleName;
    private final String field;      // e.g., "amount"
    private final double threshold;  // e.g., 10000.0

    @Override
    public Optional<Alert> evaluate(Event event) {
        Objects.requireNonNull(event, "Event must not be null");

        Optional<Double> value = event.getNumericField(field);

        if (value.isEmpty()) {
            // Field not present — skip silently (not an error)
            LOG.trace("Rule [{}]: field '{}' not present – skipping", ruleName, field);
            return Optional.empty();
        }

        double v = value.get();

        if (v > threshold) {
            LOG.debug("Rule [{}] fired: {}={} > threshold={}", ruleName, field, v, threshold);
            return Optional.of(Alert.builder()
                    .ruleName(ruleName)
                    .key(field + "=" + v)
                    .timestamp(event.getIngestionTime() != null
                            ? event.getIngestionTime() : Instant.now())
                    .details(String.format(
                            "Threshold exceeded: %s=%.2f (threshold: %.2f)",
                            field, v, threshold))
                    .originalEvent(event.getFields())
                    .build());
        }

        return Optional.empty();
    }
}
```

**Key decisions:**
- Uses **`TRACE` for skipped events** and **`DEBUG` for fired alerts** — production logging won't be flooded
- Falls back to `Instant.now()` if ingestion time is missing (defensive coding)
- The alert key is set to `field=value` (e.g., `amount=15000.0`) for identifying which field triggered the alert

---

### 3.4 RateSpikeDetector — Sliding Window Counting

**What it does:** Fires when the number of events for a key exceeds a threshold within a time window.

**When to use:** Velocity checks — brute-force detection, DDoS protection, rapid-fire abuse.

**This detector is stateful:** It maintains a `Deque<Long>` of event timestamps.

```java
public class RateSpikeDetector implements AnomalyDetector {
    private final String ruleName;
    private final String keyField;        // e.g., "userId"
    private final int windowSeconds;      // e.g., 10
    private final double threshold;       // e.g., 5

    // Sliding window of event timestamps (epoch millis)
    private final Deque<Long> timestamps = new ArrayDeque<>();

    @Override
    public Optional<Alert> evaluate(Event event) {
        long now = event.getIngestionTime() != null
                ? event.getIngestionTime().toEpochMilli()
                : System.currentTimeMillis();

        long windowStart = now - (windowSeconds * 1_000L);

        // 1. EVICT: Remove timestamps outside the window
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        // 2. RECORD: Add this event
        timestamps.addLast(now);

        // 3. CHECK: Are we over the threshold?
        int count = timestamps.size();
        if (count > threshold) {
            return Optional.of(Alert.builder()
                    .ruleName(ruleName)
                    .key(event.getStringField(keyField).orElse("unknown"))
                    .timestamp(Instant.ofEpochMilli(now))
                    .details(String.format(
                            "Rate spike: %d events in %d seconds (threshold: %.0f)",
                            count, windowSeconds, threshold))
                    .originalEvent(event.getFields())
                    .build());
        }

        return Optional.empty();
    }
}
```

**Algorithm: Sliding Window via Deque**

```
Time: ───[──────Window (10s)──────]──▶
Events:   ■ ■ ■     ■  ■  ■  ■  ■
          ↑ evict   ↑ ... kept ... ↑ new event added

1. Remove all timestamps older than (now - windowSeconds)
2. Add the current event's timestamp
3. If count > threshold → fire alert
```

**Why `ArrayDeque` instead of `ArrayList`?**
- Deques provide O(1) insertion at the tail and O(1) removal from the head — perfect for a sliding window
- `ArrayList` removal from the front is O(n) because it shifts all remaining elements

---

### 3.5 StatisticalOutlierDetector — Moving Average & Standard Deviation

**What it does:** Fires when a value deviates from the recent moving average by more than `deviationFactor × σ`.

**When to use:** Context-aware anomalies — "this value is normal in general, but unusual for *this specific key*."

**This detector is stateful:** It maintains a `Deque<Double>` of recent values.

```java
public class StatisticalOutlierDetector implements AnomalyDetector {
    static final int MIN_HISTORY_SIZE = 2;  // Need at least 2 values for stddev

    private final String ruleName;
    private final String field;              // e.g., "amount"
    private final int windowSize;            // e.g., 20
    private final double deviationFactor;    // e.g., 2.5

    private final Deque<Double> window = new ArrayDeque<>();

    @Override
    public Optional<Alert> evaluate(Event event) {
        Optional<Double> optValue = event.getNumericField(field);
        if (optValue.isEmpty()) return Optional.empty();

        double value = optValue.get();
        Optional<Alert> result = Optional.empty();

        // Only check AFTER we have enough history
        if (window.size() >= MIN_HISTORY_SIZE) {
            double mean = computeMean();
            double stddev = computeStdDev(mean);

            // If stddev is 0 (all values identical), any different value is an outlier
            double allowedDeviation = stddev == 0 ? 0 : deviationFactor * stddev;
            double diff = Math.abs(value - mean);

            if (diff > allowedDeviation) {
                result = Optional.of(Alert.builder()
                    .ruleName(ruleName)
                    .key(field + "=" + value)
                    .timestamp(event.getIngestionTime() != null
                            ? event.getIngestionTime() : Instant.now())
                    .details(String.format(
                        "Statistical outlier: %s=%.2f (mean=%.2f, stddev=%.2f, factor=%.1f)",
                        field, value, mean, stddev, deviationFactor))
                    .originalEvent(event.getFields())
                    .build());
            }
        }

        // Update window AFTER check — current value doesn't influence its own evaluation
        window.addLast(value);
        if (window.size() > windowSize) {
            window.pollFirst();
        }

        return result;
    }
}
```

**Critical design detail: Evaluate BEFORE adding to window**

```
Window:  [100, 102, 99, 105, 98]  ← history
New value: 5000                    ← evaluate AGAINST the window
                                   ← then ADD to the window
```

If we added the value to the window first, a massive outlier would increase the mean and stddev, making it harder to detect itself. By evaluating first, we ensure the outlier is compared against a "clean" baseline.

**Why `MIN_HISTORY_SIZE = 2`?** Standard deviation requires at least 2 data points. With 1 value, stddev is always 0, which would make every subsequent value look like an outlier (since `diff > 0 = allowedDeviation`).

**Statistics helpers:**

```java
private double computeMean() {
    double sum = 0;
    for (double v : window) sum += v;
    return sum / window.size();
}

private double computeStdDev(double mean) {
    double sumSquaredDiff = 0;
    for (double v : window) {
        double diff = v - mean;
        sumSquaredDiff += diff * diff;
    }
    return Math.sqrt(sumSquaredDiff / window.size());  // Population stddev
}
```

**Why population stddev (÷ N) instead of sample stddev (÷ N-1)?** In a sliding window context, we're computing the deviation of the entire window, not estimating from a sample. Population stddev is the correct choice here.

---

### 3.6 DetectorFactory — The Single Extension Point

**What it does:** Creates the right detector instance based on the rule's `type` field.

```java
public final class DetectorFactory {

    private DetectorFactory() {}  // Utility class, not instantiable

    public static AnomalyDetector create(DetectionRule rule) {
        Objects.requireNonNull(rule, "DetectionRule must not be null");
        Objects.requireNonNull(rule.getType(), "Rule type must not be null");

        String type = rule.getType().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "rate"        -> new RateSpikeDetector(rule);
            case "threshold"   -> new ThresholdDetector(rule);
            case "statistical" -> new StatisticalOutlierDetector(rule);
            default -> throw new IllegalArgumentException(
                    "Unknown rule type: '" + rule.getType() + "'");
        };
    }

    public static List<AnomalyDetector> createAll(List<DetectionRule> rules) {
        Objects.requireNonNull(rules, "Rules list must not be null");
        LOG.info("Creating {} detector(s) from configuration", rules.size());
        return Collections.unmodifiableList(
                rules.stream().map(DetectorFactory::create).toList());
    }
}
```

**Why a Factory instead of reflection/service loader?**
- Simplicity: No classpath scanning, no annotation processing, no magic
- Type safety: Adding a new type requires a compile-time change
- Discoverability: A developer can read this one `switch` statement to see all supported types

**To add a new detector type**, you only need to:
1. Implement `AnomalyDetector`
2. Add one `case` line in this switch

---

### 3.7 Configuration Loading: RulesConfig & RulesLoader

**`RulesConfig.java`** — The top-level POJO that SnakeYAML deserializes into:

```java
public class RulesConfig implements Serializable {
    private List<DetectionRule> rules = new ArrayList<>();

    // Returns UNMODIFIABLE list
    public List<DetectionRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    // Setter makes a DEFENSIVE COPY
    public void setRules(List<DetectionRule> rules) {
        this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
    }

    // Validates every rule, collecting ALL errors before throwing
    public void validate() { /* ... */ }
}
```

**`RulesLoader.java`** — Loads rules from file system or classpath:

```java
public final class RulesLoader {
    public static final String ENV_RULES_PATH = "RULES_CONFIG_PATH";

    // Resolution order:
    // 1. RULES_CONFIG_PATH env var → file system
    // 2. Classpath "rules.yml"
    public static RulesConfig load() {
        String envPath = System.getenv(ENV_RULES_PATH);
        if (envPath != null && !envPath.isBlank() && Files.exists(Path.of(envPath))) {
            return fromFile(envPath);
        }
        return fromClasspath("rules.yml");
    }

    private static RulesConfig parseAndValidate(InputStream is) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);  // Catch YAML typos
        Yaml yaml = new Yaml(new Constructor(RulesConfig.class, options));
        RulesConfig config = yaml.load(is);

        if (config == null || config.getRules() == null || config.getRules().isEmpty()) {
            LOG.warn("No detection rules defined in configuration");
            config = new RulesConfig();
        } else {
            config.validate();  // FAIL FAST on bad rules
        }
        return config;
    }
}
```

**Why `allowDuplicateKeys(false)`?** YAML silently overwrites duplicate keys by default. This catches typos where a developer accidentally writes `threshold:` twice in the same rule.

**Why validate immediately after parsing?** The application **fails fast** at startup if rules are misconfigured, rather than silently processing events with broken rules and producing no alerts.

---

## 4. Module 2: flink-job — The Streaming Pipeline

The `flink-job` module connects the `core-engine` detection library to the Flink streaming runtime, handling:
- Kafka consumption and production
- Event keying and partitioning
- Keyed state management for per-user detectors
- Checkpointing for fault tolerance
- Prometheus metrics and health endpoints

---

### 4.1 StreamSentinelJob — The Main Entry Point

This is the `main()` method that Flink's runtime calls when the job is submitted.

```java
public static void main(String[] args) throws Exception {
    // 1. Load configuration from environment variables
    JobConfig config = JobConfig.fromEnvironment();

    // 2. Load detection rules from YAML
    RulesConfig rulesConfig = loadRules(config);
    List<DetectionRule> rules = rulesConfig.getRules();

    // 3. Start health server for Kubernetes liveness/readiness probes
    HealthServer healthServer = new HealthServer();
    healthServer.start(config.getHealthPort());
    Runtime.getRuntime().addShutdownHook(new Thread(healthServer::stop));

    // 4. Configure the Flink execution environment
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.setParallelism(config.getParallelism());
    configureCheckpointing(env, config);

    // 5. Build and execute the pipeline
    buildPipeline(env, config, rules);
    env.execute("Stream Sentinel – Anomaly Detection");
}
```

**The pipeline construction:**

```java
static void buildPipeline(StreamExecutionEnvironment env,
                           JobConfig config,
                           List<DetectionRule> rules) {
    // SOURCE: Kafka events topic → Event objects
    KafkaSource<Event> kafkaSource = KafkaSource.<Event>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setTopics(config.getKafkaInputTopic())
            .setGroupId(config.getKafkaGroupId())
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new EventDeserializationSchema())
            .build();

    DataStream<Event> events = env.fromSource(
            kafkaSource,
            WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                    .withIdleness(Duration.ofMinutes(1)),
            "kafka-events-source");

    // KEY BY: Partition by configurable field (e.g., userId)
    String keyField = config.getDefaultKeyField();
    DataStream<Alert> alerts = events
            .filter(Objects::nonNull)  // Drop deserialization failures
            .keyBy(event -> event.getStringField(keyField).orElse("__unknown__"))
            .process(new AnomalyProcessFunction(rules))
            .name("anomaly-detection");

    // SINK: Alert objects → Kafka alerts topic
    KafkaSink<Alert> kafkaSink = KafkaSink.<Alert>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(
                    KafkaRecordSerializationSchema.builder()
                            .setTopic(config.getKafkaAlertTopic())
                            .setValueSerializationSchema(new AlertSerializationSchema())
                            .build())
            .build();

    alerts.sinkTo(kafkaSink).name("kafka-alerts-sink");
}
```

**Key Flink concepts used:**

| Concept | What It Does | Why It Matters |
|---|---|---|
| `WatermarkStrategy.forBoundedOutOfOrderness(5s)` | Allows events to arrive up to 5 seconds out of order | Handles network delays without dropping events |
| `.withIdleness(1m)` | Advances watermarks even when no events arrive | Prevents idle partitions from stalling the watermark |
| `.filter(Objects::nonNull)` | Drops `null` (failed deserialization) | A single bad message doesn't crash the pipeline |
| `.keyBy(event -> ...)` | Partitions by user/entity | Each key gets its own isolated detector state |

**Checkpointing configuration:**

```java
private static void configureCheckpointing(StreamExecutionEnvironment env, JobConfig config) {
    long interval = config.getCheckpointIntervalMs();
    env.enableCheckpointing(interval, CheckpointingMode.EXACTLY_ONCE);

    CheckpointConfig cpConfig = env.getCheckpointConfig();
    cpConfig.setMinPauseBetweenCheckpoints(interval / 2);
    cpConfig.setCheckpointTimeout(interval * 2);
    cpConfig.setMaxConcurrentCheckpoints(1);
    // Keep checkpoints on cancellation so state can be restored
    cpConfig.setExternalizedCheckpointCleanup(
            CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
}
```

**Why `RETAIN_ON_CANCELLATION`?** When you cancel a Flink job (e.g., to deploy a new version), checkpoints are preserved. You can resume from the last checkpoint with a new version, keeping all detector state (rate windows, moving averages) intact.

---

### 4.2 JobConfig — Environment-Driven Configuration

**Design:** Immutable configuration object built via the **Builder pattern**, resolved from environment variables.

```java
public static JobConfig fromEnvironment() {
    return new Builder()
            .kafkaBootstrapServers(env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"))
            .kafkaInputTopic(env("KAFKA_INPUT_TOPIC", "events"))
            .kafkaAlertTopic(env("KAFKA_ALERT_TOPIC", "alerts"))
            .kafkaGroupId(env("KAFKA_GROUP_ID", "stream-sentinel"))
            .parallelism(parseIntEnv("FLINK_PARALLELISM", "1"))
            .checkpointIntervalMs(parseLongEnv("FLINK_CHECKPOINT_INTERVAL_MS", "60000"))
            .rulesConfigPath(env("RULES_CONFIG_PATH", ""))
            .healthPort(parseIntEnv("HEALTH_PORT", "8080"))
            .defaultKeyField(env("DEFAULT_KEY_FIELD", "userId"))
            .build();
}
```

**Why environment variables instead of a config file?**
- **12-Factor App compliance:** Configuration is stored in the environment, not in code
- **Kubernetes-native:** K8s ConfigMaps and Secrets are injected as env vars
- **Docker-friendly:** `docker run -e KEY=value` is the standard way to configure containers

**The Builder validates at build time:**

```java
public JobConfig build() {
    Objects.requireNonNull(kafkaBootstrapServers, "kafkaBootstrapServers required");
    requireNonBlank(kafkaInputTopic, "kafkaInputTopic");
    if (parallelism < 1)
        throw new IllegalArgumentException("parallelism must be >= 1");
    if (healthPort < 1 || healthPort > 65_535)
        throw new IllegalArgumentException("healthPort must be in [1, 65535]");
    return new JobConfig(this);
}
```

---

### 4.3 EventDeserializationSchema — Kafka Bytes to Event

```java
public class EventDeserializationSchema implements DeserializationSchema<Event> {

    private transient ObjectMapper mapper;  // Marked transient — recreated after deserialization

    @Override
    public Event deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) return null;
        try {
            Event event = objectMapper().readValue(message, Event.class);
            event.setIngestionTime(Instant.now());  // Stamp arrival time
            return event;
        } catch (Exception e) {
            LOG.warn("Failed to deserialize event – skipping: {}", e.getMessage());
            return null;  // Don't crash — return null and filter downstream
        }
    }

    private ObjectMapper objectMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }
        return mapper;
    }
}
```

**Why is `ObjectMapper` transient?** ObjectMapper is not `Serializable`. When Flink checkpoints and restores this schema, the mapper would fail serialization. By marking it `transient` and lazily recreating it, the schema survives Flink's serialization lifecycle.

**Why return `null` on errors instead of throwing?** Flink's default behavior on exceptions is to restart the entire job. Returning `null` (which is then filtered out by `.filter(Objects::nonNull)`) means one bad message doesn't crash the pipeline processing millions of good messages.

---

### 4.4 AnomalyProcessFunction — The Heart of the Pipeline

This is the **most critical class** in the system. It's a Flink `KeyedProcessFunction` that:
1. Maintains per-key detector state
2. Runs every detector against every event
3. Emits alerts to the output stream
4. Tracks operational metrics

```java
public class AnomalyProcessFunction
        extends KeyedProcessFunction<String, Event, Alert> {

    private final List<DetectionRule> rules;  // Immutable config
    private transient ValueState<List<AnomalyDetector>> detectorsState;  // Per-key state
    private transient SentinelMetrics metrics;

    @Override
    public void open(Configuration parameters) {
        // Register keyed state — Flink manages this per-key
        ValueStateDescriptor<List<AnomalyDetector>> descriptor =
                new ValueStateDescriptor<>("anomaly-detectors",
                        TypeInformation.of((Class<List<AnomalyDetector>>) (Class<?>) List.class));
        detectorsState = getRuntimeContext().getState(descriptor);

        // Register custom Flink metrics
        metrics = new SentinelMetrics(getRuntimeContext().getMetricGroup());
    }

    @Override
    public void processElement(Event event, Context ctx, Collector<Alert> out)
            throws Exception {
        long startNanos = System.nanoTime();

        // Lazily initialise detectors for this key on FIRST event
        List<AnomalyDetector> detectors = detectorsState.value();
        if (detectors == null) {
            detectors = new ArrayList<>(DetectorFactory.createAll(rules));
            detectorsState.update(detectors);
        }

        // Run EVERY detector
        for (AnomalyDetector detector : detectors) {
            try {
                Optional<Alert> alert = detector.evaluate(event);
                if (alert.isPresent()) {
                    Alert a = alert.get();
                    a.setKey(ctx.getCurrentKey());  // Enrich with Flink's key
                    out.collect(a);
                    metrics.incrementAnomaliesDetected();
                }
            } catch (Exception e) {
                // One broken detector DOES NOT crash the pipeline
                LOG.error("Detector [{}] threw exception – continuing",
                        detector.getRuleName(), e);
            }
        }

        // Persist updated state (detectors may have mutated internal counters)
        detectorsState.update(detectors);

        metrics.incrementEventsProcessed();
        metrics.recordLatency((System.nanoTime() - startNanos) / 1_000_000);
    }
}
```

**Why `ValueState<List<AnomalyDetector>>`?**

Flink's **keyed state** automatically partitions data by key. When `processElement` is called for `userId=user_001`, Flink provides `user_001`'s state. When called for `user_002`, it provides `user_002`'s state. This means:

- Each user has their **own set of detectors** with independent counters and windows
- User A's rate count doesn't affect User B's
- State is **automatically checkpointed** and restored on failure

**Why lazy initialization?**

- Detectors are created on the **first event** for each key, not for all possible keys upfront
- This is important because keys are unbounded (millions of unique users)
- No memory wasted on keys that never appear

**Why catch exceptions around each detector?**

```java
try {
    Optional<Alert> alert = detector.evaluate(event);
} catch (Exception e) {
    LOG.error("Detector [{}] threw exception – continuing", detector.getRuleName(), e);
}
```

If one detector has a bug that throws `NullPointerException`, the other detectors still run. Without this, one misconfigured rule would disable all detection for that key.

---

### 4.5 AlertSerializationSchema — Alert to Kafka Bytes

```java
public class AlertSerializationSchema implements SerializationSchema<Alert> {

    private transient ObjectMapper mapper;

    @Override
    public byte[] serialize(Alert alert) {
        try {
            return objectMapper().writeValueAsBytes(alert);
        } catch (Exception e) {
            LOG.error("Failed to serialize alert: {}", e.getMessage(), e);
            return new byte[0];  // Don't crash — publish empty message
        }
    }

    private ObjectMapper objectMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        }
        return mapper;
    }
}
```

**Why `WRITE_DATES_AS_TIMESTAMPS = false`?** Without this, Jackson writes `Instant` as a numeric timestamp (`1705312860`). With it disabled, the output is ISO-8601 (`"2024-01-15T10:01:00Z"`) — human-readable and unambiguous across time zones.

---

### 4.6 SentinelMetrics — Observability

```java
public class SentinelMetrics {
    private final Counter eventsProcessed;
    private final Counter anomaliesDetected;
    private final Histogram processingLatency;

    public SentinelMetrics(MetricGroup metricGroup) {
        MetricGroup sentinelGroup = metricGroup.addGroup("stream_sentinel");

        this.eventsProcessed = sentinelGroup.counter("events_processed_total");
        this.anomaliesDetected = sentinelGroup.counter("anomalies_detected_total");
        this.processingLatency = sentinelGroup
                .histogram("processing_latency_ms",
                        new DescriptiveStatisticsHistogram(350));
    }
}
```

**Why Flink metrics instead of a separate Prometheus client?**

- Flink has a **built-in Prometheus reporter** that exposes metrics at a `/metrics` endpoint
- Using Flink's native `MetricGroup` means metrics survive task failover and restart
- No additional HTTP server needed — the Flink cluster handles metric export

**Why 350-sample histogram?** `DescriptiveStatisticsHistogram(350)` keeps the last 350 latency measurements and computes p50/p95/p99 from them. This provides meaningful percentiles without unbounded memory growth.

| Metric | Type | What It Tells You |
|---|---|---|
| `events_processed_total` | Counter | Total throughput — "how many events have we seen?" |
| `anomalies_detected_total` | Counter | Alert volume — "how many anomalies have we found?" |
| `processing_latency_ms` | Histogram | Performance — "how fast are we processing each event?" |

---

### 4.7 HealthServer — Kubernetes Probes

```java
public class HealthServer {
    private static final byte[] HEALTH_RESPONSE =
            "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);

    public void start(int port) {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", HealthServer::handleHealthCheck);
        server.createContext("/readiness", HealthServer::handleHealthCheck);

        // Daemon thread — won't prevent JVM shutdown
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "health-server");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    private static void handleHealthCheck(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, HEALTH_RESPONSE.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(HEALTH_RESPONSE);
        }
    }
}
```

**Why `com.sun.net.httpserver.HttpServer`?**

- It's **built into the JDK** — zero external dependencies
- Flink fat JARs should be minimal to reduce deployment size
- For a simple `200 OK` response, Jetty/Netty/Undertow would be massive overkill

**Why daemon threads?** When Flink shuts down the JVM, daemon threads are automatically stopped. This prevents the health server from keeping the JVM alive after the Flink job has completed.

---

## 5. Technology Stack & Why Each Was Chosen

| Technology | Version | Why Chosen |
|---|---|---|
| **Java 17** | 17+ | Records, switch expressions, pattern matching (`instanceof`), sealed classes. LTS release with modern syntax. |
| **Apache Flink** | 1.18.1 | Industry-standard stream processor. Exactly-once semantics, managed keyed state, back-pressure, built-in checkpointing. |
| **Apache Kafka** | 3.6.1 | De-facto standard event broker. Durable, scalable, widely adopted. |
| **Jackson** | 2.16.1 | Best-in-class JSON library for Java. `@JsonAnySetter` enables schema-free parsing. `JavaTimeModule` handles `Instant` serialization. |
| **SnakeYAML** | 2.2 | Lightweight YAML parser. Direct POJO binding via `Constructor`. No Spring dependency. |
| **SLF4J + Logback** | 2.0.11 / 1.4.14 | SLF4J is the de-facto logging facade. Logback is the native implementation with structured logging support. |
| **Prometheus** | 0.16.0 | Industry-standard metrics format. Flink's built-in Prometheus reporter broadcasts metrics without custom code. |
| **JUnit 5 + AssertJ** | 5.10.1 / 3.25.1 | JUnit 5 for modern testing (parameterized tests, lifecycle hooks). AssertJ for fluent, readable assertions. |
| **Maven** | 3.8+ | Multi-module project support, `maven-shade-plugin` for Flink fat JARs, dependency management. |

---

## 6. Data Flow: End-to-End

Here is the complete lifecycle of a single event:

```
Step 1: Producer publishes JSON to Kafka "events" topic
        {"userId":"user_001","amount":15000,"category":"luxury"}

Step 2: Flink KafkaSource reads the raw bytes
        byte[] → EventDeserializationSchema

Step 3: Deserialization → Event object
        ObjectMapper.readValue(bytes, Event.class)
        Sets ingestionTime = Instant.now()

Step 4: Flink keys the event by "userId"
        keyBy(event → event.getStringField("userId")) → "user_001"

Step 5: AnomalyProcessFunction.processElement()
        ├── Loads/creates per-key detectors from ValueState
        ├── Runs ThresholdDetector  → amount=15000 > 10000 → ALERT!
        ├── Runs RateSpikeDetector  → 1 event in window → no alert
        ├── Runs StatisticalOutlier → not enough history → no alert
        └── Updates state, records metrics

Step 6: Alert emitted to output stream
        Alert{ruleName="high_amount", key="user_001", details="..."}

Step 7: AlertSerializationSchema → JSON bytes

Step 8: Flink KafkaSink publishes to "alerts" topic
        {"ruleName":"high_amount","key":"user_001","timestamp":"2024-01-15T10:01:00Z",...}
```

---

## 7. How to Extend Stream Sentinel

### Adding a New Detector

**Step 1:** Create a new class implementing `AnomalyDetector`:

```java
package com.streamsentinel.core.detection;

public class GeoVelocityDetector implements AnomalyDetector {

    private final String ruleName;
    // ... your state ...

    public GeoVelocityDetector(DetectionRule rule) {
        this.ruleName = rule.getName();
        // ... init from rule config ...
    }

    @Override
    public Optional<Alert> evaluate(Event event) {
        // Your detection logic here
        return Optional.empty();
    }

    @Override
    public String getRuleName() { return ruleName; }
}
```

**Step 2:** Register in `DetectorFactory`:

```diff
 return switch (type) {
     case "rate"        -> new RateSpikeDetector(rule);
     case "threshold"   -> new ThresholdDetector(rule);
     case "statistical" -> new StatisticalOutlierDetector(rule);
+    case "geovelocity" -> new GeoVelocityDetector(rule);
     default -> throw new IllegalArgumentException(...);
 };
```

**Step 3:** Add a rule in `rules.yml`:

```yaml
- name: impossible_travel
  type: geovelocity
  field: location
  threshold: 500  # km/h max travel speed
```

**Step 4:** Write tests!

---

## 8. Testing Strategy

### What's Tested (18 unit tests)

| Test Class | Tests | What's Covered |
|---|---|---|
| `ThresholdDetectorTest` | 5 | Fire, no-fire, edge cases (exactly at threshold), missing field, null event |
| `RateSpikeDetectorTest` | 3 | Burst detection, window eviction (events expire), under-threshold |
| `StatisticalOutlierDetectorTest` | 4 | Outlier detection, insufficient data warm-up, normal values, edge cases |
| `DetectorFactoryTest` | 4 | Correct instantiation per type, unknown type error, null handling |
| `RulesLoaderTest` | 2 | YAML classpath loading, missing resource error handling |

### Running Tests

```bash
mvn test                          # Run all tests
mvn test -pl core-engine          # Run only core-engine tests
mvn surefire-report:report        # Generate HTML report
```

---

## 9. Deployment & Infrastructure

### Docker (Multi-stage Build)

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
COPY pom.xml ./
COPY core-engine/pom.xml core-engine/
COPY flink-job/pom.xml flink-job/
RUN mvn dependency:go-offline -B          # Cache dependencies
COPY core-engine/src core-engine/src
COPY flink-job/src flink-job/src
RUN mvn clean package -DskipTests -B      # Build fat JAR

# Stage 2: Runtime
FROM flink:1.18.1-java17
COPY --from=build /app/flink-job/target/flink-job-*.jar /opt/flink/usrlib/stream-sentinel.jar
COPY config/ /opt/flink/conf/stream-sentinel/
```

**Why multi-stage?** The build stage uses a Maven image (~800MB). The final image is a Flink base image (~500MB) with just the fat JAR. Without multi-stage, you'd ship the Maven toolchain, source code, and `.m2` cache in production.

**Why copy POMs first?** Docker caches layers. By copying POMs and running `dependency:go-offline` before copying source code, dependencies are only re-downloaded when POM files change — dramatically speeding up rebuild times.

### Kubernetes Manifests

The `k8s/` directory provides production-ready manifests:

| Manifest | Purpose |
|---|---|
| `namespace.yml` | Isolate Stream Sentinel resources |
| `configmap.yml` | Externalize `rules.yml` as a K8s ConfigMap |
| `deployment.yml` | Pod spec with resource limits, env vars, health probes |
| `service.yml` | ClusterIP service for health endpoint access |
| `hpa.yml` | Horizontal Pod Autoscaler based on CPU utilization |

---

*This guide covers every component, every design decision, and every line of significant code in Stream Sentinel. It should give any developer the context needed to understand, operate, debug, and extend the system.*
