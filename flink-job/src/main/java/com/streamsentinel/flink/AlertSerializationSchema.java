package com.streamsentinel.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.streamsentinel.core.model.Alert;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flink {@link SerializationSchema} that converts {@link Alert} → JSON bytes
 * for publishing to the Kafka alerts topic.
 */
public class AlertSerializationSchema implements SerializationSchema<Alert> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AlertSerializationSchema.class);

    private transient ObjectMapper mapper;

    @Override
    public byte[] serialize(Alert alert) {
        try {
            return objectMapper().writeValueAsBytes(alert);
        } catch (Exception e) {
            LOG.error("Failed to serialize alert: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    private ObjectMapper objectMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        }
        return mapper;
    }
}
