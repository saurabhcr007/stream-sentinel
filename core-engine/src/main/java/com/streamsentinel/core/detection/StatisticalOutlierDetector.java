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
 * Statistical outlier detector based on a moving average.
 *
 * <p>
 * Maintains a sliding window of the last <i>N</i> values for a given numeric
 * field. A new value is considered an outlier when it deviates from the moving
 * average by more than {@code deviationFactor × σ} (standard deviation).
 * </p>
 *
 * <h3>State</h3>
 * <p>
 * This is a <strong>stateful</strong> detector — each instance tracks the
 * observation history for one key.
 * </p>
 *
 * <h3>Warm-up</h3>
 * <p>
 * Outlier detection only engages after at least {@value #MIN_HISTORY_SIZE}
 * observations have been recorded, because standard deviation is meaningless
 * with fewer data points.
 * </p>
 *
 * @since 1.0.0
 */
public class StatisticalOutlierDetector implements AnomalyDetector {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(StatisticalOutlierDetector.class);

    /** Minimum number of observations required before outlier checks begin. */
    static final int MIN_HISTORY_SIZE = 2;

    private final String ruleName;
    private final String field;
    private final int windowSize;
    private final double deviationFactor;

    /** Sliding window of recent values. */
    private final Deque<Double> window = new ArrayDeque<>();

    /**
     * @param rule the detection rule configuration
     * @throws NullPointerException     if {@code rule} or required fields are
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code windowSize} or
     *                                  {@code deviationFactor} are invalid
     */
    public StatisticalOutlierDetector(DetectionRule rule) {
        Objects.requireNonNull(rule, "DetectionRule must not be null");
        this.ruleName = Objects.requireNonNull(rule.getName(), "Rule name must not be null");
        this.field = Objects.requireNonNull(rule.getField(),
                "Field must not be null for statistical rule '" + ruleName + "'");
        this.windowSize = rule.getWindowSize() > 0 ? rule.getWindowSize() : 10;
        this.deviationFactor = rule.getDeviationFactor() > 0 ? rule.getDeviationFactor() : 2.0;

        if (windowSize < MIN_HISTORY_SIZE) {
            throw new IllegalArgumentException(
                    "windowSize must be >= " + MIN_HISTORY_SIZE + " for rule '" + ruleName
                            + "', got: " + windowSize);
        }
    }

    @Override
    public Optional<Alert> evaluate(Event event) {
        Objects.requireNonNull(event, "Event must not be null");

        Optional<Double> optValue = event.getNumericField(field);

        if (optValue.isEmpty()) {
            LOG.trace("Rule [{}]: field '{}' not present or not numeric – skipping", ruleName, field);
            return Optional.empty();
        }

        double value = optValue.get();
        Optional<Alert> result = Optional.empty();

        // Only check after we have enough history.
        if (window.size() >= MIN_HISTORY_SIZE) {
            double mean = computeMean();
            double stddev = computeStdDev(mean);

            // A stddev of zero means all values are identical — any different value is an
            // outlier.
            double allowedDeviation = stddev == 0 ? 0 : deviationFactor * stddev;
            double diff = Math.abs(value - mean);

            if (diff > allowedDeviation) {
                LOG.debug("Rule [{}] fired: value={} mean={} stddev={} deviation={}",
                        ruleName, value, mean, stddev, diff);

                // Use event ingestion time for timestamp consistency across detectors
                Instant alertTimestamp = event.getIngestionTime() != null
                        ? event.getIngestionTime()
                        : Instant.now();

                result = Optional.of(Alert.builder()
                        .ruleName(ruleName)
                        .key(field + "=" + value)
                        .timestamp(alertTimestamp)
                        .details(String.format(
                                "Statistical outlier: %s=%.2f (mean=%.2f, stddev=%.2f, factor=%.1f)",
                                field, value, mean, stddev, deviationFactor))
                        .originalEvent(event.getFields())
                        .build());
            }
        }

        // Update window (after check so the current value doesn't influence its own
        // evaluation)
        window.addLast(value);
        if (window.size() > windowSize) {
            window.pollFirst();
        }

        return result;
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    // ---------------------------------------------------------------
    // Statistics helpers
    // ---------------------------------------------------------------

    private double computeMean() {
        double sum = 0;
        for (double v : window) {
            sum += v;
        }
        return sum / window.size();
    }

    private double computeStdDev(double mean) {
        double sumSquaredDiff = 0;
        for (double v : window) {
            double diff = v - mean;
            sumSquaredDiff += diff * diff;
        }
        return Math.sqrt(sumSquaredDiff / window.size());
    }
}
