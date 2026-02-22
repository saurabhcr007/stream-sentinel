/**
 * Apache Flink streaming job for Stream Sentinel.
 *
 * <p>
 * This package wires the core detection engine into a Flink pipeline that
 * consumes events from Kafka, runs anomaly detection per key, and publishes
 * alerts back to Kafka.
 * </p>
 *
 * <h3>Key Classes</h3>
 * <ul>
 * <li>{@link com.streamsentinel.flink.StreamSentinelJob} — main entry
 * point</li>
 * <li>{@link com.streamsentinel.flink.AnomalyProcessFunction} — keyed process
 * function</li>
 * <li>{@link com.streamsentinel.flink.JobConfig} — environment-driven
 * configuration</li>
 * <li>{@link com.streamsentinel.flink.HealthServer} — HTTP health/readiness
 * endpoints</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.streamsentinel.flink;
