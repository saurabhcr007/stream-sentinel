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
 * Unit tests for {@link RateSpikeDetector}.
 */
class RateSpikeDetectorTest {

    private RateSpikeDetector detector;

    @BeforeEach
    void setUp() {
        DetectionRule rule = new DetectionRule();
        rule.setName("high_rate");
        rule.setType("rate");
        rule.setKeyField("userId");
        rule.setWindowSeconds(10);
        rule.setThreshold(3);
        detector = new RateSpikeDetector(rule);
    }

    @Test
    @DisplayName("Should NOT fire when count is below threshold")
    void shouldNotFireBelowThreshold() {
        // Send 3 events (threshold is 3, so fire at > 3 = 4th event)
        for (int i = 0; i < 3; i++) {
            Optional<Alert> alert = detector.evaluate(createEvent(i));
            assertThat(alert).isEmpty();
        }
    }

    @Test
    @DisplayName("Should fire when count exceeds threshold within window")
    void shouldFireWhenExceedsThreshold() {
        // Send 4 events (threshold = 3, so 4th event triggers)
        Optional<Alert> alert = Optional.empty();
        for (int i = 0; i < 4; i++) {
            alert = detector.evaluate(createEvent(i));
        }
        assertThat(alert).isPresent();
        assertThat(alert.get().getRuleName()).isEqualTo("high_rate");
        assertThat(alert.get().getDetails()).contains("Rate spike");
    }

    @Test
    @DisplayName("Should evict old timestamps outside the window")
    void shouldEvictOldTimestamps() {
        Instant base = Instant.now();

        // Send 3 events at t=0
        for (int i = 0; i < 3; i++) {
            Event e = new Event();
            e.setField("userId", "u1");
            e.setIngestionTime(base);
            detector.evaluate(e);
        }

        // Send 1 event at t=11s (outside the 10s window)
        Event lateEvent = new Event();
        lateEvent.setField("userId", "u1");
        lateEvent.setIngestionTime(base.plusSeconds(11));
        Optional<Alert> alert = detector.evaluate(lateEvent);

        // Old events were evicted, so only 1 event in the window → no fire
        assertThat(alert).isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Event createEvent(int seqNum) {
        Event event = new Event();
        event.setField("userId", "user_" + seqNum);
        event.setIngestionTime(Instant.now());
        return event;
    }
}
