package com.streamsentinel.flink;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.streamsentinel.core.model.Event;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;

/**
 * Flink {@link DeserializationSchema} that converts raw Kafka bytes → {@link Event}.
 * <p>
 * Malformed messages are logged and dropped (returns {@code null}), ensuring
 * that a single bad record does not crash the entire pipeline.
 * </p>
 */
public class EventDeserializationSchema implements DeserializationSchema<Event> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(EventDeserializationSchema.class);

    private transient ObjectMapper mapper;

    @Override
    public Event deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return null;
        }
        try {
            Event event = objectMapper().readValue(message, Event.class);
            event.setIngestionTime(Instant.now());
            return event;
        } catch (Exception e) {
            LOG.warn("Failed to deserialize event – skipping: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(Event nextElement) {
        return false; // unbounded stream
    }

    @Override
    public TypeInformation<Event> getProducedType() {
        return TypeInformation.of(Event.class);
    }

    private ObjectMapper objectMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }
        return mapper;
    }
}
