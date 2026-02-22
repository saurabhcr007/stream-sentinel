package com.streamsentinel.core.detection;

import com.streamsentinel.core.model.Alert;
import com.streamsentinel.core.model.DetectionRule;
import com.streamsentinel.core.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StatisticalOutlierDetector}.
 */
class StatisticalOutlierDetectorTest {

    private StatisticalOutlierDetector detector;

    @BeforeEach
    void setUp() {
        DetectionRule rule = new DetectionRule();
        rule.setName("unusual_amount");
        rule.setType("statistical");
        rule.setField("amount");
        rule.setWindowSize(5);
        rule.setDeviationFactor(2.0);
        detector = new StatisticalOutlierDetector(rule);
    }

    @Test
    @DisplayName("Should NOT fire on first two events (insufficient data)")
    void shouldNotFireWithInsufficientData() {
        assertThat(detector.evaluate(createEvent(100))).isEmpty();
        assertThat(detector.evaluate(createEvent(110))).isEmpty();
    }

    @Test
    @DisplayName("Should NOT fire on values close to the mean")
    void shouldNotFireOnNormalValues() {
        // Build up history: values around 100
        for (int i = 0; i < 5; i++) {
            detector.evaluate(createEvent(100 + i));
        }

        // A value of 103 is well within 2σ
        Optional<Alert> alert = detector.evaluate(createEvent(103));
        assertThat(alert).isEmpty();
    }

    @Test
    @DisplayName("Should fire on an extreme outlier")
    void shouldFireOnOutlier() {
        // Build history of values around 100
        for (int i = 0; i < 5; i++) {
            detector.evaluate(createEvent(100));
        }

        // A value of 10,000 should be way beyond 2σ
        Optional<Alert> alert = detector.evaluate(createEvent(10_000));
        assertThat(alert).isPresent();
        assertThat(alert.get().getRuleName()).isEqualTo("unusual_amount");
        assertThat(alert.get().getDetails()).contains("Statistical outlier");
    }

    @Test
    @DisplayName("Should fire when all values are identical and a different value arrives")
    void shouldFireOnAnyDeviationFromConstant() {
        // All values are exactly 100 → stddev = 0
        for (int i = 0; i < 5; i++) {
            detector.evaluate(createEvent(100));
        }

        // Any value != 100 triggers because allowedDeviation = 0
        Optional<Alert> alert = detector.evaluate(createEvent(101));
        assertThat(alert).isPresent();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Event createEvent(double amount) {
        Event event = new Event();
        event.setField("amount", amount);
        event.setIngestionTime(Instant.now());
        return event;
    }
}
