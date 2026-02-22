package com.streamsentinel.core.detection;

import com.streamsentinel.core.model.Alert;
import com.streamsentinel.core.model.DetectionRule;
import com.streamsentinel.core.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Rate-spike detector.
 *
 * <p>
 * Detects when the number of events for a given key exceeds a configured
 * threshold within a sliding time window (in seconds).
 * </p>
 *
 * <h3>Implementation</h3>
 * <p>
 * Maintains a deque of event timestamps (epoch millis). On each evaluation
 * the deque is pruned to the current window, the new event is recorded, and
 * a count check is performed.
 * </p>
 *
 * <h3>State</h3>
 * <p>
 * This detector is <strong>stateful</strong>. One instance is held per
 * key in Flink keyed state.
 * </p>
 *
 * @since 1.0.0
 */
public class RateSpikeDetector implements AnomalyDetector {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(RateSpikeDetector.class);

    private final String ruleName;
    private final String keyField;
    private final int windowSeconds;
    private final double threshold;

    /** Sliding window of event timestamps (epoch millis). */
    private final Deque<Long> timestamps = new ArrayDeque<>();

    /**
     * @param rule the detection rule configuration
     * @throws NullPointerException     if {@code rule} is {@code null}
     * @throws IllegalArgumentException if {@code windowSeconds} or
     *                                  {@code threshold} are invalid
     */
    public RateSpikeDetector(DetectionRule rule) {
        Objects.requireNonNull(rule, "DetectionRule must not be null");
        this.ruleName = Objects.requireNonNull(rule.getName(), "Rule name must not be null");
        this.keyField = rule.getKeyField(); // may be null; key comes from Flink context
        this.windowSeconds = rule.getWindowSeconds();
        this.threshold = rule.getThreshold();

        if (windowSeconds <= 0) {
            throw new IllegalArgumentException(
                    "windowSeconds must be > 0 for rule '" + ruleName + "', got: " + windowSeconds);
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException(
                    "threshold must be > 0 for rule '" + ruleName + "', got: " + threshold);
        }
    }

    @Override
    public Optional<Alert> evaluate(Event event) {
        Objects.requireNonNull(event, "Event must not be null");

        long now = event.getIngestionTime() != null
                ? event.getIngestionTime().toEpochMilli()
                : System.currentTimeMillis();

        long windowStart = now - (windowSeconds * 1_000L);

        // Evict timestamps outside the window
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        // Record this event
        timestamps.addLast(now);

        int count = timestamps.size();

        if (count > threshold) {
            LOG.debug("Rule [{}] fired: count={} > threshold={}", ruleName, count, threshold);

            // Use the configured keyField for alert metadata when available
            String alertKey = (keyField != null)
                    ? event.getStringField(keyField).orElse("unknown")
                    : "unknown";

            return Optional.of(Alert.builder()
                    .ruleName(ruleName)
                    .key(alertKey)
                    .timestamp(Instant.ofEpochMilli(now))
                    .details(String.format(
                            "Rate spike: %d events in %d seconds (threshold: %.0f)",
                            count, windowSeconds, threshold))
                    .originalEvent(event.getFields())
                    .build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }
}
