# Changelog

All notable changes to Stream Sentinel will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Multi-module Maven project (`core-engine`, `flink-job`)
- Pluggable anomaly detection engine with three built-in rule types:
  - **Rate spike** — events per key exceeding threshold within a time window
  - **Threshold** — numeric field exceeding a static limit
  - **Statistical outlier** — value deviating from moving average
- Kafka integration (consumer + producer) via Flink Kafka connector
- YAML-based rule configuration with environment variable override
- Flink keyed-state processing with exactly-once checkpointing
- Prometheus-compatible metrics (`events_processed_total`, `anomalies_detected_total`, `processing_latency_ms`)
- HTTP health/readiness endpoints for Kubernetes probes
- Multi-stage Dockerfile (Maven build → Flink 1.18 runtime)
- Production Kubernetes manifests (Namespace, ConfigMap, Deployment, Service, HPA)
- Comprehensive unit test suite (JUnit 5 + AssertJ)
- Input validation and fail-fast rule checking at startup
- Defensive coding throughout (null safety, immutable collections, builder validation)
- Apache 2.0 license, CONTRIBUTING.md, and package-level Javadoc
