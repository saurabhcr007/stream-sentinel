/**
 * Domain model classes for Stream Sentinel.
 *
 * <p>
 * This package contains the data transfer objects shared between the
 * detection engine and the Flink job layer:
 * </p>
 * <ul>
 * <li>{@link com.streamsentinel.core.model.Event} — generic JSON event
 * wrapper</li>
 * <li>{@link com.streamsentinel.core.model.Alert} — anomaly alert emitted by
 * detectors</li>
 * <li>{@link com.streamsentinel.core.model.DetectionRule} — rule configuration
 * POJO</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.streamsentinel.core.model;
