package com.streamsentinel.core.detection;

import com.streamsentinel.core.model.Alert;
import com.streamsentinel.core.model.DetectionRule;
import com.streamsentinel.core.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Threshold detector.
 *
 * <p>
 * Fires an alert when a numeric field value exceeds the configured threshold.
 * This is a <strong>stateless</strong> detector — each event is evaluated
 * independently.
 * </p>
 *
 * @since 1.0.0
 */
public class ThresholdDetector implements AnomalyDetector {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ThresholdDetector.class);

    private final String ruleName;
    private final String field;
    private final double threshold;

    /**
     * @param rule the detection rule configuration
     * @throws NullPointerException if {@code rule} or required fields are
     *                              {@code null}
     */
    public ThresholdDetector(DetectionRule rule) {
        Objects.requireNonNull(rule, "DetectionRule must not be null");
        this.ruleName = Objects.requireNonNull(rule.getName(), "Rule name must not be null");
        this.field = Objects.requireNonNull(rule.getField(),
                "Field must not be null for threshold rule '" + ruleName + "'");
        this.threshold = rule.getThreshold();
    }

    @Override
    public Optional<Alert> evaluate(Event event) {
        Objects.requireNonNull(event, "Event must not be null");

        Optional<Double> value = event.getNumericField(field);

        if (value.isEmpty()) {
            LOG.trace("Rule [{}]: field '{}' not present or not numeric – skipping", ruleName, field);
            return Optional.empty();
        }

        double v = value.get();

        if (v > threshold) {
            LOG.debug("Rule [{}] fired: {}={} > threshold={}", ruleName, field, v, threshold);

            // Use event ingestion time for timestamp consistency across detectors
            Instant alertTimestamp = event.getIngestionTime() != null
                    ? event.getIngestionTime()
                    : Instant.now();

            return Optional.of(Alert.builder()
                    .ruleName(ruleName)
                    .key(field + "=" + v)
                    .timestamp(alertTimestamp)
                    .details(String.format(
                            "Threshold exceeded: %s=%.2f (threshold: %.2f)", field, v, threshold))
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
