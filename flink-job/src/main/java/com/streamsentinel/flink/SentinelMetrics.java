package com.streamsentinel.flink;

import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.metrics.DescriptiveStatisticsHistogram;

/**
 * Custom Flink metric definitions for Stream Sentinel.
 * <p>
 * Flink exposes these via its configured metric reporters (e.g. Prometheus).
 * The metric reporter is configured in {@code flink-conf.yaml} at cluster
 * level; the job only defines the metrics.
 * </p>
 *
 * <h3>Exposed Metrics</h3>
 * <ul>
 *   <li>{@code events_processed_total} – counter of all events evaluated</li>
 *   <li>{@code anomalies_detected_total} – counter of fired alerts</li>
 *   <li>{@code processing_latency_ms} – histogram of per-event latency</li>
 * </ul>
 */
public class SentinelMetrics {

    private final Counter eventsProcessed;
    private final Counter anomaliesDetected;
    private final Histogram processingLatency;

    public SentinelMetrics(MetricGroup metricGroup) {
        MetricGroup sentinelGroup = metricGroup.addGroup("stream_sentinel");

        this.eventsProcessed = sentinelGroup.counter("events_processed_total");
        this.anomaliesDetected = sentinelGroup.counter("anomalies_detected_total");

        // DescriptiveStatisticsHistogram keeps a sliding window (350 samples
        // by default) and exposes p50/p95/p99 etc.
        this.processingLatency = sentinelGroup
                .histogram("processing_latency_ms", new DescriptiveStatisticsHistogram(350));
    }

    public void incrementEventsProcessed() {
        eventsProcessed.inc();
    }

    public void incrementAnomaliesDetected() {
        anomaliesDetected.inc();
    }

    public void recordLatency(long milliseconds) {
        processingLatency.update(milliseconds);
    }
}
