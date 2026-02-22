package com.streamsentinel.flink;

import com.streamsentinel.core.detection.AnomalyDetector;
import com.streamsentinel.core.detection.DetectorFactory;
import com.streamsentinel.core.model.Alert;
import com.streamsentinel.core.model.DetectionRule;
import com.streamsentinel.core.model.Event;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core Flink {@link KeyedProcessFunction} that runs every configured anomaly
 * detector against incoming events.
 *
 * <p>
 * Each key (e.g. {@code userId}) gets its own copy of the detector list via
 * Flink managed keyed state. This ensures per-key isolation and automatic
 * checkpointing.
 * </p>
 *
 * <h3>State Management</h3>
 * <p>
 * A {@code ValueState<List<AnomalyDetector>>} holds the detector
 * instances per key. The detectors themselves are {@link java.io.Serializable},
 * so they are snapshotted during Flink checkpoints.
 * </p>
 *
 * <h3>Metrics</h3>
 * <p>
 * Custom Flink metrics are registered in {@link #open(Configuration)} and
 * updated on every processed event and fired alert.
 * </p>
 *
 * @since 1.0.0
 */
public class AnomalyProcessFunction
        extends KeyedProcessFunction<String, Event, Alert> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AnomalyProcessFunction.class);

    /**
     * Rule definitions (serializable config, not runtime state). Defensive copy.
     */
    private final List<DetectionRule> rules;

    /** Flink keyed state holding per-key detector instances. */
    private transient ValueState<List<AnomalyDetector>> detectorsState;

    /** Custom Flink metrics. */
    private transient SentinelMetrics metrics;

    /**
     * @param rules the detection rules to evaluate; must not be {@code null} or
     *              empty
     * @throws NullPointerException     if {@code rules} is {@code null}
     * @throws IllegalArgumentException if {@code rules} is empty
     */
    public AnomalyProcessFunction(List<DetectionRule> rules) {
        Objects.requireNonNull(rules, "Detection rules list must not be null");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("Detection rules list must not be empty");
        }
        // Defensive copy so external mutation cannot affect us
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @Override
    public void open(Configuration parameters) {
        // Register keyed state
        ValueStateDescriptor<List<AnomalyDetector>> descriptor = new ValueStateDescriptor<>(
                "anomaly-detectors",
                TypeInformation.of((Class<List<AnomalyDetector>>) (Class<?>) List.class));
        detectorsState = getRuntimeContext().getState(descriptor);

        // Register custom Flink metrics
        metrics = new SentinelMetrics(getRuntimeContext().getMetricGroup());
        LOG.info("AnomalyProcessFunction opened with {} rule(s)", rules.size());
    }

    @Override
    public void close() {
        LOG.info("AnomalyProcessFunction closing");
    }

    // ---------------------------------------------------------------
    // Processing
    // ---------------------------------------------------------------

    @Override
    public void processElement(Event event,
            KeyedProcessFunction<String, Event, Alert>.Context ctx,
            Collector<Alert> out) throws Exception {
        long startNanos = System.nanoTime();

        // Lazily initialise detectors for this key on first event
        List<AnomalyDetector> detectors = detectorsState.value();
        if (detectors == null) {
            detectors = new ArrayList<>(DetectorFactory.createAll(rules));
            detectorsState.update(detectors);
        }

        // Evaluate every detector
        for (AnomalyDetector detector : detectors) {
            try {
                Optional<Alert> alert = detector.evaluate(event);
                if (alert.isPresent()) {
                    Alert a = alert.get();
                    // Enrich alert with the keyed-stream key from Flink context
                    a.setKey(ctx.getCurrentKey());
                    out.collect(a);
                    metrics.incrementAnomaliesDetected();
                    LOG.info("Alert fired: rule={} key={}", a.getRuleName(), a.getKey());
                }
            } catch (Exception e) {
                LOG.error("Detector [{}] threw an exception – continuing with next detector",
                        detector.getRuleName(), e);
            }
        }

        // Persist updated detector state (detectors may have mutated internal state)
        detectorsState.update(detectors);

        metrics.incrementEventsProcessed();
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        metrics.recordLatency(durationMs);
    }
}
