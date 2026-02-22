package com.streamsentinel.core.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic event wrapper.
 *
 * <p>
 * Events arrive as free-form JSON from Kafka. This class stores them as a
 * {@link Map} so the detection engine can query arbitrary fields without
 * requiring a rigid schema.
 * </p>
 *
 * <h3>Thread Safety</h3>
 * <p>
 * This class is <strong>not</strong> thread-safe. A single instance must
 * only be accessed by one thread at a time, which is guaranteed by Flink's
 * single-threaded operator model.
 * </p>
 *
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Every key–value pair in the original JSON event. */
    private final Map<String, Object> fields = new LinkedHashMap<>();

    /** Ingestion timestamp (set by the deserializer). */
    private Instant ingestionTime;

    // ---------------------------------------------------------------
    // Jackson dynamic-property support
    // ---------------------------------------------------------------

    /**
     * Set a field value. Called by Jackson for every JSON property.
     *
     * @param key   the JSON key; must not be {@code null}
     * @param value the JSON value
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @JsonAnySetter
    public void setField(String key, Object value) {
        Objects.requireNonNull(key, "Field key must not be null");
        fields.put(key, value);
    }

    /**
     * Return an <strong>unmodifiable</strong> view of all fields.
     *
     * <p>
     * Callers that need a mutable copy should wrap the result
     * in {@code new LinkedHashMap<>(event.getFields())}.
     * </p>
     *
     * @return unmodifiable map of field names to values
     */
    @JsonAnyGetter
    public Map<String, Object> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    // ---------------------------------------------------------------
    // Field accessors
    // ---------------------------------------------------------------

    /**
     * Retrieve a field value by name.
     *
     * @param fieldName the JSON key
     * @return optional containing the value, or empty if not present
     */
    public Optional<Object> getField(String fieldName) {
        return Optional.ofNullable(fields.get(fieldName));
    }

    /**
     * Retrieve a numeric field value, coercing common JSON number types.
     *
     * <p>
     * Handles {@link Number} subclasses natively and attempts
     * {@link Double#parseDouble(String)} for string-encoded numbers.
     * </p>
     *
     * @param fieldName the JSON key
     * @return optional containing the value as a {@code double}
     */
    public Optional<Double> getNumericField(String fieldName) {
        Object raw = fields.get(fieldName);
        if (raw instanceof Number n) {
            return Optional.of(n.doubleValue());
        }
        if (raw instanceof String s) {
            try {
                return Optional.of(Double.parseDouble(s));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieve a string field value.
     *
     * @param fieldName the JSON key
     * @return optional containing the string value
     */
    public Optional<String> getStringField(String fieldName) {
        Object raw = fields.get(fieldName);
        return raw == null ? Optional.empty() : Optional.of(raw.toString());
    }

    // ---------------------------------------------------------------
    // Ingestion time
    // ---------------------------------------------------------------

    /**
     * @return the ingestion timestamp, or {@code null} if not yet set
     */
    public Instant getIngestionTime() {
        return ingestionTime;
    }

    /**
     * @param ingestionTime the instant at which the event was ingested
     */
    public void setIngestionTime(Instant ingestionTime) {
        this.ingestionTime = ingestionTime;
    }

    // ---------------------------------------------------------------
    // equals / hashCode / toString
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Event that))
            return false;
        return Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

    @Override
    public String toString() {
        return "Event" + fields;
    }
}
