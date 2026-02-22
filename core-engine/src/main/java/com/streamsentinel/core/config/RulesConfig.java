package com.streamsentinel.core.config;

import com.streamsentinel.core.model.DetectionRule;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Top-level POJO for the rules YAML configuration.
 *
 * <p>
 * Expected YAML structure:
 * </p>
 * 
 * <pre>
 * rules:
 *   - name: high_rate
 *     type: rate
 *     keyField: userId
 *     windowSeconds: 10
 *     threshold: 5
 * </pre>
 *
 * <p>
 * Call {@link #validate()} after loading to verify every rule is valid.
 * </p>
 *
 * @since 1.0.0
 */
public class RulesConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<DetectionRule> rules = new ArrayList<>();

    /**
     * Return the rules list. The returned list is <strong>unmodifiable</strong>.
     *
     * @return unmodifiable list of detection rules
     */
    public List<DetectionRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * Set the rules list (used by SnakeYAML during deserialization).
     *
     * @param rules the detection rules
     */
    public void setRules(List<DetectionRule> rules) {
        this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
    }

    /**
     * Validate every rule in this configuration.
     *
     * <p>
     * Delegates to {@link DetectionRule#validate()} for each rule.
     * Collects all errors and throws a single exception if any rule is invalid.
     * </p>
     *
     * @throws IllegalStateException if one or more rules are invalid
     */
    public void validate() {
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rules.size(); i++) {
            DetectionRule rule = Objects.requireNonNull(rules.get(i),
                    "Rule at index " + i + " is null");
            try {
                rule.validate();
            } catch (IllegalStateException e) {
                errors.add(e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Rules configuration validation failed:\n  - "
                            + String.join("\n  - ", errors));
        }
    }

    @Override
    public String toString() {
        return "RulesConfig{rules=" + rules + '}';
    }
}
