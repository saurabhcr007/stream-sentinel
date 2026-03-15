# Stream Sentinel — TODO

> Prioritized backlog of improvements, bug fixes, and feature requests.

---

## 🔴 Critical

- [ ] **Add flink-job unit tests** — The `flink-job` module has zero test coverage. Add tests for:
  - `EventDeserializationSchema` (valid JSON, malformed JSON, empty/null bytes)
  - `AlertSerializationSchema` (round-trip serialization, null alert)
  - `JobConfig.Builder` validation (out-of-range values, blank strings)
  - `HealthServer` (start/stop lifecycle, port conflict handling)

- [ ] **HealthServer silently swallows startup `IOException`** — If port binding fails, the server logs the error but the job continues running without health endpoints. Kubernetes probes will fail with no clear indication of root cause. Should propagate the exception.

- [ ] **AlertSerializationSchema returns empty `byte[0]` on failure** — Kafka treats this as a valid (empty) message, producing ghost records that confuse downstream consumers. Should return `null` or rethrow.

---

## 🟡 Performance

- [ ] **StatisticalOutlierDetector: O(n) → O(1) mean/stddev** — Currently iterates the entire window deque on every `evaluate()` call. Replace with Welford's online algorithm to track running sum/sumSquares incrementally.

- [ ] **DetectorFactory.createAll(): redundant unmodifiable wrapping** — `.toList()` already returns an unmodifiable list; the additional `Collections.unmodifiableList()` wrapper is unnecessary.

- [ ] **ObjectMapper caching in serialization schemas** — Both `EventDeserializationSchema` and `AlertSerializationSchema` lazily create `ObjectMapper` instances. Consider a shared, thread-safe singleton to reduce GC pressure under high throughput.

---

## 🟢 Robustness

- [ ] **Dead-letter queue (DLQ) for failed events** — Instead of dropping malformed events silently, route them to a separate Kafka topic for debugging and replay.

- [ ] **Alert deduplication / cooldown** — A sustained anomaly (e.g., continuous threshold breach) fires an alert on every event. Add a configurable cooldown period per rule per key.

- [ ] **Graceful degradation on rule config errors** — If one rule in `rules.yml` is invalid, the entire job fails to start. Consider skipping invalid rules with warnings while starting the rest.

- [ ] **Rate detector: protect against clock skew** — `RateSpikeDetector` falls back to `System.currentTimeMillis()` when ingestion time is null. Under Flink's event-time processing, this can cause inconsistencies.

---

## 🔵 Features

- [ ] **Hot configuration reload** — Currently, changing rules requires a savepoint + restart. Add support for:
  - Broadcast stream pattern (push new rules via a Kafka config topic)
  - Periodic file-system polling with rule diff detection

- [ ] **Alert severity levels** — Add `severity` field (INFO / WARNING / CRITICAL) to `DetectionRule` and propagate to `Alert`. Enable downstream systems to prioritize alerts.

- [ ] **Composite rules** — Support AND/OR combinations of existing rules (e.g., "rate spike AND high amount").

- [ ] **Rule-scoped key fields** — Each `DetectionRule` should be able to override the default `keyField` (currently only rate rules have `keyField`).

- [ ] **Sliding window support for rate detection** — Current rate detection uses a naive deque approach. Consider Flink's native windowing API for more accurate and scalable sliding windows.

- [ ] **REST API for rule management** — Expose CRUD endpoints for detection rules, eliminating the need for config file changes and restarts.

- [ ] **Alert notification channels** — Add integrations for email, Slack, PagerDuty, and webhooks.

---

## 🟣 DevOps & Infrastructure

- [ ] **CI/CD pipeline** — Set up GitHub Actions workflow for:
  - Build + test on PR
  - Docker image build + push on merge to main
  - Automated version tagging

- [ ] **docker-compose for local development** — Add a `docker-compose.yml` with Kafka (KRaft mode), Flink cluster, and optionally Prometheus + Grafana.

- [ ] **Grafana dashboard template** — Pre-built dashboard JSON for the three custom metrics (`events_processed_total`, `anomalies_detected_total`, `processing_latency_ms`).

- [ ] **Integration tests with Testcontainers** — Use Testcontainers to spin up Kafka and validate end-to-end: produce events → verify alerts.

- [ ] **JaCoCo code coverage report** — Add `jacoco-maven-plugin` to track and enforce minimum coverage thresholds.

---

## 🔧 Code Quality

- [ ] **Normalize indentation in `StreamSentinelJob.java`** — Uses tabs while the rest of the codebase uses 4-space indentation.

- [ ] **Add `package-info.java` for `flink` package** — The `core-engine` packages all have `package-info.java` but `flink-job` only has a generic one.

- [ ] **Checkstyle / SpotBugs / PMD** — Add static analysis plugins to enforce code style and catch common bugs.

- [ ] **JavaDoc completeness** — Some internal methods lack Javadoc. Add documentation for all public and package-private methods.

---

## 📝 Documentation

- [ ] **API versioning strategy** — Document how the alert JSON schema will evolve and backward compatibility guarantees.

- [ ] **Performance benchmarks** — Document expected throughput (events/sec) and latency at various parallelism levels.

- [ ] **Troubleshooting guide** — Common issues and their solutions (Kafka connectivity, checkpoint failures, OOM errors).

- [ ] **Architecture Decision Records (ADRs)** — Document key design decisions (why Flink over Kafka Streams, why SnakeYAML over Jackson YAML, etc.).
