package com.streamsentinel.core.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Describes a single anomaly-detection rule loaded from configuration.
 *
 * <p>
 * Supported rule types:
 * </p>
 * <ul>
 * <li>{@code rate} — rate-spike detection (events per key within a time
 * window)</li>
 * <li>{@code threshold} — static threshold on a numeric field</li>
 * <li>{@code statistical} — statistical outlier based on moving average</li>
 * </ul>
 *
 * <p>
 * Call {@link #validate()} after construction / deserialization to verify
 * that all required fields for the declared rule type are present and valid.
 * </p>
 *
 * @since 1.0.0
 */
public class DetectionRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Unique rule name used in alerts and metrics. */
    private String name;

    /** Rule type: "rate", "threshold", or "statistical". */
    private String type;

    // --- Rate-spike fields ---
    /** JSON field used as the grouping key (e.g. userId, IP). */
    private String keyField;

    /** Size of the sliding/tumbling window in seconds. */
    private int windowSeconds;

    // --- Threshold fields ---
    /** JSON field whose numeric value is evaluated. */
    private String field;

    /** Threshold value — semantics depend on the rule type. */
    private double threshold;

    // --- Statistical outlier fields ---
    /** Number of recent values to keep for the moving average. */
    private int windowSize = 10;

    /** Number of standard deviations for outlier detection. */
    private double deviationFactor = 2.0;

    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    /**
     * Validate that all required fields for the declared rule type are present
     * and contain legal values.
     *
     * @throws IllegalStateException if validation fails
     */
    public void validate() {
        List<String> errors = new ArrayList<>();

        if (name == null || name.isBlank()) {
            errors.add("Rule 'name' is required");
        }
        if (type == null || type.isBlank()) {
            errors.add("Rule 'type' is required");
        }

        if (type != null) {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "rate" -> {
                    if (keyField == null || keyField.isBlank()) {
                        errors.add("Rate rule '" + name + "' requires 'keyField'");
                    }
                    if (windowSeconds <= 0) {
                        errors.add("Rate rule '" + name + "' requires 'windowSeconds' > 0");
                    }
                    if (threshold <= 0) {
                        errors.add("Rate rule '" + name + "' requires 'threshold' > 0");
                    }
                }
                case "threshold" -> {
                    if (field == null || field.isBlank()) {
                        errors.add("Threshold rule '" + name + "' requires 'field'");
                    }
                }
                case "statistical" -> {
                    if (field == null || field.isBlank()) {
                        errors.add("Statistical rule '" + name + "' requires 'field'");
                    }
                    if (windowSize < 2) {
                        errors.add("Statistical rule '" + name + "' requires 'windowSize' >= 2");
                    }
                    if (deviationFactor <= 0) {
                        errors.add("Statistical rule '" + name + "' requires 'deviationFactor' > 0");
                    }
                }
                default -> errors.add("Unknown rule type: '" + type
                        + "'. Supported: rate, threshold, statistical");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid DetectionRule: " + String.join("; ", errors));
        }
    }

    // ---------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    /**
     * Set the rule type, normalised to lowercase.
     *
     * @param type rule type string
     */
    public void setType(String type) {
        this.type = type != null ? type.toLowerCase(Locale.ROOT) : null;
    }

    public String getKeyField() {
        return keyField;
    }

    public void setKeyField(String keyField) {
        this.keyField = keyField;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public double getDeviationFactor() {
        return deviationFactor;
    }

    public void setDeviationFactor(double deviationFactor) {
        this.deviationFactor = deviationFactor;
    }

    // ---------------------------------------------------------------
    // equals / hashCode / toString
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof DetectionRule that))
            return false;
        return Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return "DetectionRule{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", keyField='" + keyField + '\'' +
                ", field='" + field + '\'' +
                ", threshold=" + threshold +
                ", windowSeconds=" + windowSeconds +
                ", windowSize=" + windowSize +
                ", deviationFactor=" + deviationFactor +
                '}';
    }
}
