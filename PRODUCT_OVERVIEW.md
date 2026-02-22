# Stream Sentinel — Product Overview

> **One-line summary:** Stream Sentinel is an open-source, real-time anomaly detection engine that watches your event streams and fires alerts the moment something looks wrong.

---

## What Is Stream Sentinel?

Stream Sentinel is a **production-ready, cloud-native system** that sits between your event producers (applications, IoT devices, payment gateways, etc.) and your response systems (dashboards, on-call tools, automated remediation). It continuously consumes events from **Apache Kafka**, applies configurable detection rules using **Apache Flink**, and publishes structured alerts back to Kafka — all in **real time**, with **sub-second latency**.

Think of it as a **programmable watchdog** for any data stream. Instead of building custom anomaly detection logic into every microservice, you define simple YAML rules and let Stream Sentinel do the heavy lifting.

---

## Why Was Stream Sentinel Built?

### The Problem

Modern distributed systems generate enormous volumes of events — transactions, API calls, sensor readings, user activity logs. Hidden within this firehose are **anomalies** that signal fraud, system failures, security breaches, or business-critical incidents. Traditional solutions have significant drawbacks:

| Traditional Approach | Limitation |
|---|---|
| **Batch analytics** (hourly/daily) | Anomalies are detected too late to act on |
| **Custom per-service logic** | Duplicated effort, inconsistent rules, hard to maintain |
| **Commercial APM/SIEM tools** | Expensive, vendor lock-in, limited customization |
| **In-house Flink/Spark jobs** | High engineering cost, no reusable framework |

### The Solution

Stream Sentinel provides a **reusable, declarative framework** where:

- **Rules are YAML, not code** — A product manager or analyst can define what "anomalous" means without writing Java.
- **Processing is real-time** — Sub-second latency from event ingestion to alert emission.
- **Architecture is pluggable** — Adding a new detection algorithm is a single Java interface implementation.
- **Deployment is cloud-native** — Docker images, Kubernetes manifests, Prometheus metrics, and health probes are included out of the box.
- **State is fault-tolerant** — Flink checkpointing ensures that detection state (sliding windows, moving averages) survives failures without data loss.

---

## What Can Stream Sentinel Detect?

Stream Sentinel ships with **three detection algorithms**, each suited to different anomaly patterns:

### 1. Rate Spike Detection
> *"Too many events from one user in a short time"*

Monitors the **frequency of events per key** (e.g., per user, per IP, per device) within a configurable sliding time window. Fires when the count exceeds a threshold.

**Example:** A single user making 20 API calls in 5 seconds, or a device sending 100 sensor readings in 10 seconds.

### 2. Threshold Detection
> *"A single value that's dangerously high (or low)"*

Compares a **numeric field** in each event against a static threshold. Fires immediately when the value exceeds the limit.

**Example:** A payment amount exceeding $10,000, CPU usage above 95%, or warehouse temperature above 40°C.

### 3. Statistical Outlier Detection
> *"A value that doesn't fit the recent pattern"*

Maintains a **moving average and standard deviation** over a sliding window of recent values. Fires when a new value deviates from the mean by more than a configurable factor (N × σ).

**Example:** A user who typically spends $50–$100 suddenly makes a $5,000 purchase. The absolute value may be "normal" in general, but it's an outlier *for this user*.

---

## Where Can Stream Sentinel Be Used?

Stream Sentinel is **domain-agnostic** — anywhere you have event streams, you can deploy it. Below are real-world use cases organized by industry:

### 🏦 Financial Services & Payments
| Use Case | Detection Type | Example Rule |
|---|---|---|
| Fraud detection | Threshold + Statistical | Single transaction > $10K; unusual spending pattern |
| Card-present velocity check | Rate | More than 5 transactions in 60 seconds on same card |
| Account takeover detection | Rate + Statistical | Sudden spike in login attempts from new devices |
| AML transaction monitoring | Statistical | Wire transfers deviating from historical average |

### 🛒 E-Commerce & Retail
| Use Case | Detection Type | Example Rule |
|---|---|---|
| Bot detection | Rate | More than 50 product page views per second from one IP |
| Price abuse | Threshold | Coupon discounts exceeding $500 in a single order |
| Inventory manipulation | Rate | Rapid add-to-cart / remove-from-cart cycles |
| Flash sale abuse | Rate + Threshold | Single user purchasing > 10 units of a limited item |

### 🖥️ DevOps & Infrastructure Monitoring
| Use Case | Detection Type | Example Rule |
|---|---|---|
| DDoS early warning | Rate | > 1,000 requests per second from a single source IP |
| Error rate spike | Rate | > 100 5xx errors from a single service in 30 seconds |
| Latency anomaly | Statistical | Response time deviating from moving average |
| Resource exhaustion | Threshold | Memory usage > 90%, disk I/O > threshold |

### 🏭 IoT & Manufacturing
| Use Case | Detection Type | Example Rule |
|---|---|---|
| Sensor malfunction | Statistical | Temperature reading deviating from moving average |
| Equipment failure prediction | Threshold + Statistical | Vibration levels exceeding safe limits |
| Environmental monitoring | Threshold | Air quality index above hazardous threshold |
| Connected vehicle telemetry | Rate + Statistical | Unusual GPS ping frequency or speed deviation |

### 🔒 Security & Compliance
| Use Case | Detection Type | Example Rule |
|---|---|---|
| Brute-force login detection | Rate | > 10 failed logins in 60 seconds for the same account |
| Data exfiltration | Threshold + Rate | File download size > 1GB or > 50 downloads per minute |
| Privilege escalation | Rate | Multiple permission changes in a short window |
| Insider threat detection | Statistical | Access patterns deviating from historical behavior |

### 🎮 Gaming & Social Platforms
| Use Case | Detection Type | Example Rule |
|---|---|---|
| Cheating detection | Rate + Statistical | Impossible action frequency or score progression |
| Spam detection | Rate | > 20 messages per minute from a single user |
| Fake engagement | Rate | Abnormal like/share velocity on new content |

---

## How Does It Fit Into Your Architecture?

Stream Sentinel is designed to be a **transparent, middleware layer** in any event-driven architecture:

```
┌──────────────────┐     ┌──────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Your Services   │────▶│    Kafka      │────▶│ Stream Sentinel  │────▶│  Alert Kafka    │
│  (producers)     │     │  (events)    │     │  (Flink job)     │     │  Topic          │
└──────────────────┘     └──────────────┘     └──────────────────┘     └────────┬────────┘
                                                       │                        │
                                               ┌───────┴───────┐      ┌────────▼────────┐
                                               │  Prometheus   │      │  Your Consumers │
                                               │  + Grafana    │      │  (Slack, PD,    │
                                               └───────────────┘      │   email, SIEM)  │
                                                                      └─────────────────┘
```

### Key Integration Points

| Integration | How |
|---|---|
| **Input** | Any JSON event published to a Kafka topic |
| **Output** | Structured JSON alerts on a separate Kafka topic |
| **Monitoring** | Prometheus metrics (events processed, anomalies detected, latency) |
| **Health checks** | HTTP `/health` and `/readiness` for Kubernetes liveness/readiness probes |
| **Deployment** | Docker image + full Kubernetes manifests (Deployment, Service, HPA, ConfigMap) |

---

## Key Advantages

| Advantage | Details |
|---|---|
| **Zero-code rule creation** | Define rules in YAML — no Java knowledge needed for operators |
| **Sub-second latency** | Real-time processing via Apache Flink streaming |
| **Fault-tolerant state** | Flink checkpointing preserves detection state across failures |
| **Horizontally scalable** | Increase Flink parallelism to handle higher event volumes |
| **Per-key isolation** | Each user/entity gets independent detection state |
| **Pluggable detectors** | Add custom algorithms by implementing one Java interface |
| **Production-ready** | Docker, K8s manifests, Prometheus metrics, health probes included |
| **Open source (Apache 2.0)** | Free to use, modify, and distribute commercially |

---

## Summary

Stream Sentinel fills the gap between raw event streams and actionable intelligence. Whether you're detecting fraudulent transactions, infrastructure anomalies, or IoT sensor malfunctions, Stream Sentinel gives you a **battle-tested, real-time anomaly detection engine** that deploys in minutes and scales to millions of events per second.

**Start simple → Define YAML rules → Deploy to Kubernetes → Get alerts in real time.**
