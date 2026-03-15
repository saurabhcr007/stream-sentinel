<div align="center">
  <img src="https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/shield-check.svg" alt="Stream Sentinel Logo" width="80" height="80">
  <h1>Stream Sentinel</h1>
  <p><strong>Cloud-Native Real-Time Anomaly Detection Engine & Analytics Dashboard</strong></p>
  
  [![Build - Core Engine](https://github.com/yourusername/stream-sentinel/actions/workflows/ci-core-engine.yml/badge.svg)](https://github.com/yourusername/stream-sentinel/actions/workflows/ci-core-engine.yml)
  [![Build - API Gateway](https://github.com/yourusername/stream-sentinel/actions/workflows/ci-api-gateway.yml/badge.svg)](https://github.com/yourusername/stream-sentinel/actions/workflows/ci-api-gateway.yml)
  [![Build - Frontend](https://github.com/yourusername/stream-sentinel/actions/workflows/ci-frontend.yml/badge.svg)](https://github.com/yourusername/stream-sentinel/actions/workflows/ci-frontend.yml)
</div>

<br />

Stream Sentinel is an enterprise-grade, highly scalable platform designed to ingest immense volumes of streaming event data, detect anomalies in sub-millisecond latencies, and visualize those insights in real-time.

Built from the ground up for high-availability cloud deployments, it leverages a robust **Java/Apache Flink** analytics pipeline, a rapid **Node.js/Socket.IO API Gateway**, and a premium **React/Vite** management dashboard, fully orchestrated by **Kubernetes and Docker**.

---

## 🏗️ Architecture & Tech Stack

![Architecture Overview](https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/network.svg)

### Data Engineering & Analytics Pipeline
- **Apache Kafka**: High-throughput distributed event bus for ingesting raw data (`events` topic) and publishing detected anomalies (`alerts` topic).
- **Apache Flink (Java 17)**: Stateful stream processing engine. Handles exact-once semantics, dynamic rule evaluation (Rate Spike, Threshold, Statistical Outlier), and complex event keyed processing.

### API Middleware & Real-Time Sync
- **Node.js & Express**: High-performance REST API Gateway enabling dynamic configuration management.
- **Socket.IO**: Maintains persistent WebSockets to push anomaly analytics from Kafka directly to the user's browser without polling.

### Premium Frontend Experience
- **React 18 & Vite**: Ultra-fast Single Page Application (SPA).
- **Recharts & Lucide Icons**: Dynamic real-time charts and beautiful glassmorphism-inspired dark mode UI.

### DevOps, SRE & Cloud-Native Delivery
- **Docker Compose**: Unified local orchestrator enabling 1-click stack deployments (Zookeeper, Kafka, Flink, Gateway, React, Prometheus, Grafana).
- **Kubernetes (K8s)**: Production-ready declarative manifests (`Deployment`, `Service`, `HPA`, `Ingress`, `ConfigMap`) ensuring zero-downtime scaling.
- **GitHub Actions**: Modular GitFlow CI/CD pipelines. Features isolated PR testing per microservice and automated multi-registry Docker image publishing (`ghcr.io` & Docker Hub).

---

## ⚡ Core Features

1. **Pluggable Real-Time Detection Engine**
   - Implements `Threshold`, `Rate Spike`, and `Statistical Outlier` rule patterns dynamically parsed from external YAML configurations.
2. **Sub-Millisecond Alerting Pipeline**
   - Detects fraudulent activity, system failures, or anomalous user behavior and propagates the alert from the data stream to the React UI in under 1 second using WebSockets.
3. **Responsive Visual Dashboard**
   - High-fidelity metrics visualizations including event processing throughput, recent anomaly history, and interactive Rule Management.
4. **DevOps Automation**
   - Fully containerized microservices utilizing Docker multi-stage builds (`npm ci`, `--omit=dev`) to guarantee optimized and secure production deployments.
5. **Observability Built-In**
   - Exposes native `/health` probes tailored for Kubernetes workloads and detailed `events_processed_total` metrics natively integrated with Prometheus.

---

## 🚀 Getting Started

### Local 1-Click Environment
The entire 8-container stack is orchestrated for seamless local execution.

1. Ensure **Docker** and **Docker Compose** are installed.
2. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/stream-sentinel.git
   cd stream-sentinel
   ```
3. Boot the unified local environment:
   ```bash
   docker-compose up -d
   ```
4. Access the React Dashboard at `http://localhost`.

### Producing Sample Data
If you modify the Kafka environment or want to push manual data:
```bash
cat examples/sample-events.json | jq -c '.[]' | \
  docker exec -i sentinel-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 --topic events
```

---

## 🌐 Project Structure

```bash
stream-sentinel/
├── api-gateway/       # Node.js Express server + Socket.IO
├── config/            # Detection Engine rules.yml (Live sync)
├── core-engine/       # Core Anomaly Detection Java Library
├── docker/            # Optimized Dockerfiles & Build contexts 
├── flink-job/         # Apache Flink Streaming Topology (Java)
├── frontend/          # React + Vite Dashboard SPA
├── k8s/               # Production Kubernetes manifests
└── .github/workflows/ # Modular GitFlow CI/CD actions
```

---

## 👨‍💻 About The Developer

This project was built from scratch to demonstrate full-stack engineering proficiency spanning **Data Streaming, Backend Microservices, Modern Frontend Development, and SRE/DevOps principles**. 

It encapsulates patterns frequently required in large-scale technical deployments: distributed systems orchestration, real-time UI synchronization, and robust CI/CD automation.

<br />

> **Note to Recruiters/Hiring Managers**: 
> *If you're reviewing this repository, it represents my ability to lead and execute complex, end-to-end software architectures—from Kafka clusters down to React CSS Modules and GitHub Actions workflows.*
