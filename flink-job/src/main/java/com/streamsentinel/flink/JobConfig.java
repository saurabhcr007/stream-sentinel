package com.streamsentinel.flink;

import java.io.Serializable;
import java.util.Objects;
import java.util.Properties;

/**
 * Typed, immutable configuration object for the Stream Sentinel Flink job.
 *
 * <p>
 * Values are resolved from environment variables with sensible defaults.
 * This makes the job fully configurable via Kubernetes Deployment env vars,
 * Docker {@code -e} flags, or a shell environment.
 * </p>
 *
 * <h3>Construction</h3>
 * <p>
 * Use {@link #fromEnvironment()} for production, or the {@link Builder}
 * for programmatic / test scenarios. The builder validates inputs at
 * {@link Builder#build()} time.
 * </p>
 *
 * @since 1.0.0
 */
public final class JobConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------------------------------------------------------------
    // Kafka
    // ---------------------------------------------------------------
    private final String kafkaBootstrapServers;
    private final String kafkaInputTopic;
    private final String kafkaAlertTopic;
    private final String kafkaGroupId;

    // ---------------------------------------------------------------
    // Flink
    // ---------------------------------------------------------------
    private final int parallelism;
    private final long checkpointIntervalMs;

    // ---------------------------------------------------------------
    // Rules
    // ---------------------------------------------------------------
    private final String rulesConfigPath;

    // ---------------------------------------------------------------
    // Health / Metrics
    // ---------------------------------------------------------------
    private final int healthPort;

    // ---------------------------------------------------------------
    // Key field (default event key)
    // ---------------------------------------------------------------
    private final String defaultKeyField;

    private JobConfig(Builder b) {
        this.kafkaBootstrapServers = b.kafkaBootstrapServers;
        this.kafkaInputTopic = b.kafkaInputTopic;
        this.kafkaAlertTopic = b.kafkaAlertTopic;
        this.kafkaGroupId = b.kafkaGroupId;
        this.parallelism = b.parallelism;
        this.checkpointIntervalMs = b.checkpointIntervalMs;
        this.rulesConfigPath = b.rulesConfigPath;
        this.healthPort = b.healthPort;
        this.defaultKeyField = b.defaultKeyField;
    }

    // ---------------------------------------------------------------
    // Factory — resolve from environment
    // ---------------------------------------------------------------

    /**
     * Build a {@link JobConfig} from environment variables.
     *
     * @return fully populated configuration
     * @throws IllegalStateException    if an env-var value cannot be parsed
     * @throws IllegalArgumentException if a validated field is out of range
     */
    public static JobConfig fromEnvironment() {
        try {
            return new Builder()
                    .kafkaBootstrapServers(env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"))
                    .kafkaInputTopic(env("KAFKA_INPUT_TOPIC", "events"))
                    .kafkaAlertTopic(env("KAFKA_ALERT_TOPIC", "alerts"))
                    .kafkaGroupId(env("KAFKA_GROUP_ID", "stream-sentinel"))
                    .parallelism(parseIntEnv("FLINK_PARALLELISM", "1"))
                    .checkpointIntervalMs(parseLongEnv("FLINK_CHECKPOINT_INTERVAL_MS", "60000"))
                    .rulesConfigPath(env("RULES_CONFIG_PATH", ""))
                    .healthPort(parseIntEnv("HEALTH_PORT", "8080"))
                    .defaultKeyField(env("DEFAULT_KEY_FIELD", "userId"))
                    .build();
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Failed to parse numeric environment variable: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // Kafka properties helpers
    // ---------------------------------------------------------------

    /**
     * Build Kafka consumer {@link Properties}.
     *
     * @return new Properties instance configured for consumption
     */
    public Properties kafkaConsumerProperties() {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", kafkaBootstrapServers);
        props.setProperty("group.id", kafkaGroupId);
        props.setProperty("auto.offset.reset", "earliest");
        return props;
    }

    /**
     * Build Kafka producer {@link Properties}.
     *
     * @return new Properties instance configured for production
     */
    public Properties kafkaProducerProperties() {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", kafkaBootstrapServers);
        props.setProperty("transaction.timeout.ms", "900000");
        return props;
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public String getKafkaInputTopic() {
        return kafkaInputTopic;
    }

    public String getKafkaAlertTopic() {
        return kafkaAlertTopic;
    }

    public String getKafkaGroupId() {
        return kafkaGroupId;
    }

    public int getParallelism() {
        return parallelism;
    }

    public long getCheckpointIntervalMs() {
        return checkpointIntervalMs;
    }

    public String getRulesConfigPath() {
        return rulesConfigPath;
    }

    public int getHealthPort() {
        return healthPort;
    }

    public String getDefaultKeyField() {
        return defaultKeyField;
    }

    // ---------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------

    /**
     * Fluent builder for {@link JobConfig}.
     *
     * <p>
     * The {@link #build()} method validates that all values are within legal
     * ranges (parallelism &gt; 0, checkpoint interval &gt; 0, port in
     * [1, 65535], non-blank topic names).
     * </p>
     */
    public static class Builder {
        private String kafkaBootstrapServers = "localhost:9092";
        private String kafkaInputTopic = "events";
        private String kafkaAlertTopic = "alerts";
        private String kafkaGroupId = "stream-sentinel";
        private int parallelism = 1;
        private long checkpointIntervalMs = 60_000;
        private String rulesConfigPath = "";
        private int healthPort = 8080;
        private String defaultKeyField = "userId";

        public Builder kafkaBootstrapServers(String v) {
            this.kafkaBootstrapServers = v;
            return this;
        }

        public Builder kafkaInputTopic(String v) {
            this.kafkaInputTopic = v;
            return this;
        }

        public Builder kafkaAlertTopic(String v) {
            this.kafkaAlertTopic = v;
            return this;
        }

        public Builder kafkaGroupId(String v) {
            this.kafkaGroupId = v;
            return this;
        }

        public Builder parallelism(int v) {
            this.parallelism = v;
            return this;
        }

        public Builder checkpointIntervalMs(long v) {
            this.checkpointIntervalMs = v;
            return this;
        }

        public Builder rulesConfigPath(String v) {
            this.rulesConfigPath = v;
            return this;
        }

        public Builder healthPort(int v) {
            this.healthPort = v;
            return this;
        }

        public Builder defaultKeyField(String v) {
            this.defaultKeyField = v;
            return this;
        }

        /**
         * Build and validate the configuration.
         *
         * @return a validated {@link JobConfig}
         * @throws IllegalArgumentException if any value is invalid
         */
        public JobConfig build() {
            Objects.requireNonNull(kafkaBootstrapServers, "kafkaBootstrapServers required");
            requireNonBlank(kafkaInputTopic, "kafkaInputTopic");
            requireNonBlank(kafkaAlertTopic, "kafkaAlertTopic");
            requireNonBlank(kafkaGroupId, "kafkaGroupId");
            requireNonBlank(defaultKeyField, "defaultKeyField");

            if (parallelism < 1) {
                throw new IllegalArgumentException("parallelism must be >= 1, got: " + parallelism);
            }
            if (checkpointIntervalMs < 1) {
                throw new IllegalArgumentException(
                        "checkpointIntervalMs must be >= 1, got: " + checkpointIntervalMs);
            }
            if (healthPort < 1 || healthPort > 65_535) {
                throw new IllegalArgumentException(
                        "healthPort must be in [1, 65535], got: " + healthPort);
            }

            return new JobConfig(this);
        }

        private static void requireNonBlank(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be null or blank");
            }
        }
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private static int parseIntEnv(String name, String defaultValue) {
        return Integer.parseInt(env(name, defaultValue));
    }

    private static long parseLongEnv(String name, String defaultValue) {
        return Long.parseLong(env(name, defaultValue));
    }

    @Override
    public String toString() {
        return "JobConfig{" +
                "kafkaBootstrapServers='" + kafkaBootstrapServers + '\'' +
                ", kafkaInputTopic='" + kafkaInputTopic + '\'' +
                ", kafkaAlertTopic='" + kafkaAlertTopic + '\'' +
                ", kafkaGroupId='" + kafkaGroupId + '\'' +
                ", parallelism=" + parallelism +
                ", checkpointIntervalMs=" + checkpointIntervalMs +
                ", healthPort=" + healthPort +
                ", defaultKeyField='" + defaultKeyField + '\'' +
                '}';
    }
}
