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
 * Unit tests for {@link ThresholdDetector}.
 */
class ThresholdDetectorTest {

    private ThresholdDetector detector;

    @BeforeEach
    void setUp() {
        DetectionRule rule = new DetectionRule();
        rule.setName("high_amount");
        rule.setType("threshold");
        rule.setField("amount");
        rule.setThreshold(10_000);
        detector = new ThresholdDetector(rule);
    }

    @Test
    @DisplayName("Should fire when value exceeds threshold")
    void shouldFireWhenExceedsThreshold() {
        Event event = createEvent("amount", 15_000);
        Optional<Alert> alert = detector.evaluate(event);

        assertThat(alert).isPresent();
        assertThat(alert.get().getRuleName()).isEqualTo("high_amount");
        assertThat(alert.get().getDetails()).contains("Threshold exceeded");
    }

    @Test
    @DisplayName("Should NOT fire when value is below threshold")
    void shouldNotFireWhenBelowThreshold() {
        Event event = createEvent("amount", 5_000);
        Optional<Alert> alert = detector.evaluate(event);

        assertThat(alert).isEmpty();
    }

    @Test
    @DisplayName("Should NOT fire when value equals threshold exactly")
    void shouldNotFireAtExactThreshold() {
        Event event = createEvent("amount", 10_000);
        Optional<Alert> alert = detector.evaluate(event);

        assertThat(alert).isEmpty();
    }

    @Test
    @DisplayName("Should NOT fire when field is missing")
    void shouldNotFireWhenFieldMissing() {
        Event event = createEvent("other_field", 99_999);
        Optional<Alert> alert = detector.evaluate(event);

        assertThat(alert).isEmpty();
    }

    @Test
    @DisplayName("Should handle string-encoded numeric values")
    void shouldHandleStringEncodedNumbers() {
        Event event = new Event();
        event.setField("amount", "20000");
        event.setIngestionTime(Instant.now());

        Optional<Alert> alert = detector.evaluate(event);
        assertThat(alert).isPresent();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Event createEvent(String field, double value) {
        Event event = new Event();
        event.setField(field, value);
        event.setIngestionTime(Instant.now());
        return event;
    }
}
