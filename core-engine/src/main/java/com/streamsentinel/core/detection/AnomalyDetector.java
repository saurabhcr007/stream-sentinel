package com.streamsentinel.core.detection;

import com.streamsentinel.core.model.Alert;
import com.streamsentinel.core.model.Event;

import java.io.Serializable;
import java.util.Optional;

/**
 * Contract for all anomaly detectors.
 * <p>
 * Implementations are expected to be <strong>stateful</strong>: each instance
 * is bound to a single key (e.g. userId) and accumulates observations across
 * consecutive calls to {@link #evaluate(Event)}.
 * </p>
 * <p>
 * Detectors must be {@link Serializable} because Flink snapshots them in
 * checkpointed keyed state.
 * </p>
 */
public interface AnomalyDetector extends Serializable {

    /**
     * Evaluate a single event and decide whether it constitutes an anomaly.
     *
     * @param event the incoming event
     * @return an {@link Alert} if the event triggers the rule, empty otherwise
     */
    Optional<Alert> evaluate(Event event);

    /**
     * Return the unique name of the rule this detector enforces.
     *
     * @return rule name
     */
    String getRuleName();
}
