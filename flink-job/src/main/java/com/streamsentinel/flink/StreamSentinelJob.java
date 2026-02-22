package com.streamsentinel.flink;

import com.streamsentinel.core.config.RulesConfig;
import com.streamsentinel.core.config.RulesLoader;
import com.streamsentinel.core.model.Alert;
import com.streamsentinel.core.model.DetectionRule;
import com.streamsentinel.core.model.Event;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Main entry point for the Stream Sentinel Flink job.
 *
 * <h3>Pipeline</h3>
 * 
 * <pre>
 *   Kafka (events topic)
 *     → Deserialize JSON → Event
 *     → Key by configurable field (e.g. userId)
 *     → AnomalyProcessFunction (runs all detection rules)
 *     → Serialize Alert → JSON
 *     → Kafka (alerts topic)
 * </pre>
 *
 * <h3>Configuration</h3>
 * <p>
 * All configuration is resolved from environment variables via
 * {@link JobConfig}.
 * </p>
 *
 * <h3>Checkpointing</h3>
 * <p>
 * Exactly-once semantics are enabled when checkpointing is active. This
 * ensures that Flink keyed state (detection-rule accumulators) survives
 * failures.
 * </p>
 *
 * @since 1.0.0
 */
public final class StreamSentinelJob {

        private static final Logger LOG = LoggerFactory.getLogger(StreamSentinelJob.class);

        private StreamSentinelJob() {
                // entry-point class — not instantiable
        }

        public static void main(String[] args) throws Exception {
                // 1. Load configuration
                JobConfig config = JobConfig.fromEnvironment();
                LOG.info("Starting Stream Sentinel with config: {}", config);

                // 2. Load detection rules
                RulesConfig rulesConfig = loadRules(config);
                List<DetectionRule> rules = rulesConfig.getRules();

                if (rules.isEmpty()) {
                        throw new IllegalStateException(
                                        "No detection rules defined. Provide rules via "
                                                        + RulesLoader.ENV_RULES_PATH
                                                        + " or a classpath rules.yml file.");
                }
                LOG.info("Loaded {} detection rule(s)", rules.size());

                // 3. Start health server (for K8s probes) with shutdown hook
                HealthServer healthServer = new HealthServer();
                healthServer.start(config.getHealthPort());
                Runtime.getRuntime().addShutdownHook(new Thread(healthServer::stop, "health-shutdown"));

                // 4. Set up Flink execution environment
                StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
                env.setParallelism(config.getParallelism());
                configureCheckpointing(env, config);

                // 5. Build pipeline
                buildPipeline(env, config, rules);

                // 6. Execute
                env.execute("Stream Sentinel – Anomaly Detection");
        }

        // ---------------------------------------------------------------
        // Pipeline assembly (extracted for readability and testability)
        // ---------------------------------------------------------------

        /**
         * Build the full Kafka → Flink → Kafka pipeline.
         */
        static void buildPipeline(StreamExecutionEnvironment env,
                        JobConfig config,
                        List<DetectionRule> rules) {
                // Kafka source
                KafkaSource<Event> kafkaSource = KafkaSource.<Event>builder()
                                .setBootstrapServers(config.getKafkaBootstrapServers())
                                .setTopics(config.getKafkaInputTopic())
                                .setGroupId(config.getKafkaGroupId())
                                .setStartingOffsets(OffsetsInitializer.earliest())
                                .setValueOnlyDeserializer(new EventDeserializationSchema())
                                .build();

                DataStream<Event> events = env.fromSource(
                                kafkaSource,
                                WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                                .withIdleness(Duration.ofMinutes(1)),
                                "kafka-events-source");

                // Key by configurable field & process
                String keyField = config.getDefaultKeyField();
                DataStream<Alert> alerts = events
                                .filter(Objects::nonNull) // drop deserialization failures
                                .keyBy(event -> event.getStringField(keyField).orElse("__unknown__"))
                                .process(new AnomalyProcessFunction(rules))
                                .name("anomaly-detection");

                // Kafka sink for alerts
                KafkaSink<Alert> kafkaSink = KafkaSink.<Alert>builder()
                                .setBootstrapServers(config.getKafkaBootstrapServers())
                                .setRecordSerializer(
                                                KafkaRecordSerializationSchema.builder()
                                                                .setTopic(config.getKafkaAlertTopic())
                                                                .setValueSerializationSchema(
                                                                                new AlertSerializationSchema())
                                                                .build())
                                .build();

                alerts.sinkTo(kafkaSink).name("kafka-alerts-sink");
        }

        // ---------------------------------------------------------------
        // Helpers
        // ---------------------------------------------------------------

        private static RulesConfig loadRules(JobConfig config) {
                String rulesPath = config.getRulesConfigPath();
                if (rulesPath != null && !rulesPath.isBlank()) {
                        return RulesLoader.fromFile(rulesPath);
                }
                return RulesLoader.load();
        }

        private static void configureCheckpointing(StreamExecutionEnvironment env, JobConfig config) {
                long interval = config.getCheckpointIntervalMs();
                env.enableCheckpointing(interval, CheckpointingMode.EXACTLY_ONCE);

                CheckpointConfig cpConfig = env.getCheckpointConfig();
                cpConfig.setMinPauseBetweenCheckpoints(interval / 2);
                cpConfig.setCheckpointTimeout(interval * 2);
                cpConfig.setMaxConcurrentCheckpoints(1);
                // Retain checkpoints on cancellation so state can be restored
                cpConfig.setExternalizedCheckpointCleanup(
                                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        }
}
