package com.streamsentinel.core.detection;

import com.streamsentinel.core.model.DetectionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DetectorFactory}.
 */
class DetectorFactoryTest {

    @Test
    @DisplayName("Should create RateSpikeDetector for type=rate")
    void shouldCreateRateDetector() {
        DetectionRule rule = ruleOfType("rate");
        AnomalyDetector detector = DetectorFactory.create(rule);
        assertThat(detector).isInstanceOf(RateSpikeDetector.class);
    }

    @Test
    @DisplayName("Should create ThresholdDetector for type=threshold")
    void shouldCreateThresholdDetector() {
        DetectionRule rule = ruleOfType("threshold");
        rule.setField("amount");
        AnomalyDetector detector = DetectorFactory.create(rule);
        assertThat(detector).isInstanceOf(ThresholdDetector.class);
    }

    @Test
    @DisplayName("Should create StatisticalOutlierDetector for type=statistical")
    void shouldCreateStatisticalDetector() {
        DetectionRule rule = ruleOfType("statistical");
        rule.setField("amount");
        AnomalyDetector detector = DetectorFactory.create(rule);
        assertThat(detector).isInstanceOf(StatisticalOutlierDetector.class);
    }

    @Test
    @DisplayName("Should throw for unknown type")
    void shouldThrowForUnknownType() {
        DetectionRule rule = ruleOfType("magic");
        assertThatThrownBy(() -> DetectorFactory.create(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown rule type");
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private DetectionRule ruleOfType(String type) {
        DetectionRule rule = new DetectionRule();
        rule.setName("test_rule");
        rule.setType(type);
        rule.setKeyField("userId");
        rule.setWindowSeconds(10);
        rule.setThreshold(5);
        return rule;
    }
}
