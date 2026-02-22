/**
 * Configuration loading and validation for Stream Sentinel detection rules.
 *
 * <p>
 * Rules are defined in YAML and loaded by
 * {@link com.streamsentinel.core.config.RulesLoader} into a
 * {@link com.streamsentinel.core.config.RulesConfig} instance. Validation
 * is performed automatically after parsing to ensure fail-fast behaviour.
 * </p>
 *
 * @since 1.0.0
 */
package com.streamsentinel.core.config;
