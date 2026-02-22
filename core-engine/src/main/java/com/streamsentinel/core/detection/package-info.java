/**
 * Pluggable anomaly detection engine.
 *
 * <p>
 * All detectors implement the
 * {@link com.streamsentinel.core.detection.AnomalyDetector}
 * interface and are instantiated via
 * {@link com.streamsentinel.core.detection.DetectorFactory}.
 * Built-in detector types:
 * </p>
 * <ul>
 * <li>{@link com.streamsentinel.core.detection.RateSpikeDetector} — event rate
 * within a sliding window</li>
 * <li>{@link com.streamsentinel.core.detection.ThresholdDetector} — static
 * numeric threshold</li>
 * <li>{@link com.streamsentinel.core.detection.StatisticalOutlierDetector} —
 * moving average ± N × σ</li>
 * </ul>
 *
 * <h3>Extending</h3>
 * <p>
 * To add a new rule type, implement {@code AnomalyDetector} and register
 * the type string in {@code DetectorFactory.create()}.
 * </p>
 *
 * @since 1.0.0
 */
package com.streamsentinel.core.detection;
