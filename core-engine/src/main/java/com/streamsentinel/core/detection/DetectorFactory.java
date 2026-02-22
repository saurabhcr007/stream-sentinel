package com.streamsentinel.core.detection;

import com.streamsentinel.core.model.DetectionRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Factory that creates {@link AnomalyDetector} instances from
 * {@link DetectionRule} configurations.
 *
 * <p>
 * This is the single point of extension when adding new rule types:
 * register the new type string here and create the corresponding detector.
 * </p>
 *
 * @since 1.0.0
 */
public final class DetectorFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DetectorFactory.class);

    private DetectorFactory() {
        // utility class — not instantiable
    }

    /**
     * Create a detector for the given rule.
     *
     * @param rule the detection rule configuration; must not be {@code null}
     * @return an appropriate {@link AnomalyDetector} instance
     * @throws NullPointerException     if {@code rule} or its type is {@code null}
     * @throws IllegalArgumentException if the rule type is unknown
     */
    public static AnomalyDetector create(DetectionRule rule) {
        Objects.requireNonNull(rule, "DetectionRule must not be null");
        Objects.requireNonNull(rule.getType(), "Rule type must not be null");

        String type = rule.getType().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "rate" -> new RateSpikeDetector(rule);
            case "threshold" -> new ThresholdDetector(rule);
            case "statistical" -> new StatisticalOutlierDetector(rule);
            default -> throw new IllegalArgumentException(
                    "Unknown rule type: '" + rule.getType()
                            + "'. Supported types: rate, threshold, statistical");
        };
    }

    /**
     * Create detectors for every rule in the supplied list.
     *
     * <p>
     * The returned list is <strong>unmodifiable</strong>.
     * </p>
     *
     * @param rules list of rule configurations; must not be {@code null}
     * @return unmodifiable list of detectors (one per rule)
     * @throws NullPointerException if {@code rules} is {@code null}
     */
    public static List<AnomalyDetector> createAll(List<DetectionRule> rules) {
        Objects.requireNonNull(rules, "Rules list must not be null");
        LOG.info("Creating {} detector(s) from configuration", rules.size());
        List<AnomalyDetector> detectors = rules.stream()
                .map(DetectorFactory::create)
                .toList();
        return Collections.unmodifiableList(detectors);
    }
}
