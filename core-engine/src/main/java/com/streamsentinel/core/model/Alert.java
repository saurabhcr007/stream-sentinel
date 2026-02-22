package com.streamsentinel.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Alert emitted when an anomaly detection rule fires.
 *
 * <p>
 * Serialized to JSON and published to the configured Kafka alerts topic.
 * </p>
 *
 * <h3>Construction</h3>
 * <p>
 * Use the {@link Builder} to construct instances. The builder enforces that
 * {@code ruleName} and {@code timestamp} are present; omitting either will
 * throw a {@link NullPointerException} at build time.
 * </p>
 *
 * @since 1.0.0
 */
public class Alert implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Name of the detection rule that triggered the alert. */
    private String ruleName;

    /** The key value (e.g. userId, IP) that caused the alert. */
    private String key;

    /** Timestamp when the anomaly was detected. */
    private Instant timestamp;

    /** Human-readable description of what was detected. */
    private String details;

    /** Defensive copy of the original event that triggered the anomaly. */
    private Map<String, Object> originalEvent;

    // ---------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------

    /** No-arg constructor required by Jackson. */
    public Alert() {
    }

    private Alert(Builder builder) {
        this.ruleName = Objects.requireNonNull(builder.ruleName, "ruleName must not be null");
        this.key = builder.key;
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
        this.details = builder.details;
        // Defensive copy to prevent mutation by callers
        this.originalEvent = builder.originalEvent != null
                ? new LinkedHashMap<>(builder.originalEvent)
                : null;
    }

    // ---------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------

    /**
     * Create a new {@link Builder}.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link Alert} instances.
     *
     * <p>
     * {@code ruleName} and {@code timestamp} are <strong>required</strong>.
     * Calling {@link #build()} without them throws {@link NullPointerException}.
     * </p>
     */
    public static class Builder {
        private String ruleName;
        private String key;
        private Instant timestamp;
        private String details;
        private Map<String, Object> originalEvent;

        public Builder ruleName(String ruleName) {
            this.ruleName = ruleName;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder originalEvent(Map<String, Object> originalEvent) {
            this.originalEvent = originalEvent;
            return this;
        }

        /**
         * Build the alert.
         *
         * @return a new {@link Alert}
         * @throws NullPointerException if {@code ruleName} or {@code timestamp} is
         *                              {@code null}
         */
        public Alert build() {
            return new Alert(this);
        }
    }

    // ---------------------------------------------------------------
    // Getters / Setters (required for Jackson)
    // ---------------------------------------------------------------

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    /**
     * Return an <strong>unmodifiable</strong> view of the original event.
     *
     * @return unmodifiable map, or {@code null} if not set
     */
    public Map<String, Object> getOriginalEvent() {
        return originalEvent != null
                ? Collections.unmodifiableMap(originalEvent)
                : null;
    }

    /**
     * Set the original event (defensive copy is made internally).
     *
     * @param originalEvent the event map
     */
    public void setOriginalEvent(Map<String, Object> originalEvent) {
        this.originalEvent = originalEvent != null
                ? new LinkedHashMap<>(originalEvent)
                : null;
    }

    // ---------------------------------------------------------------
    // equals / hashCode / toString
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Alert alert))
            return false;
        return Objects.equals(ruleName, alert.ruleName)
                && Objects.equals(key, alert.key)
                && Objects.equals(timestamp, alert.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleName, key, timestamp);
    }

    @Override
    public String toString() {
        return "Alert{" +
                "ruleName='" + ruleName + '\'' +
                ", key='" + key + '\'' +
                ", timestamp=" + timestamp +
                ", details='" + details + '\'' +
                '}';
    }
}
