# =============================================================================
# Stream Sentinel – Multi-stage Dockerfile
# =============================================================================
# Stage 1: Build the project with Maven
# Stage 2: Run on an official Flink base image
# =============================================================================

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy POM files first for dependency caching
COPY pom.xml ./
COPY core-engine/pom.xml core-engine/
COPY flink-job/pom.xml flink-job/

# Download dependencies (cached unless POMs change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY core-engine/src core-engine/src
COPY flink-job/src flink-job/src

# Build the fat JAR
RUN mvn clean package -DskipTests -B

# --- Runtime stage ---
FROM flink:1.18.1-java17

# Copy the fat JAR into Flink's usrlib directory (auto-loaded on startup)
COPY --from=build /app/flink-job/target/flink-job-*.jar /opt/flink/usrlib/stream-sentinel.jar

# Copy default configuration
COPY config/ /opt/flink/conf/stream-sentinel/

# Set default environment variables
ENV KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
    KAFKA_INPUT_TOPIC=events \
    KAFKA_ALERT_TOPIC=alerts \
    KAFKA_GROUP_ID=stream-sentinel \
    FLINK_PARALLELISM=1 \
    FLINK_CHECKPOINT_INTERVAL_MS=60000 \
    RULES_CONFIG_PATH=/opt/flink/conf/stream-sentinel/rules.yml \
    HEALTH_PORT=8080 \
    DEFAULT_KEY_FIELD=userId

EXPOSE 8080

# The base Flink image will handle starting the job via the Flink entrypoint.
# For standalone mode, override CMD:
#   docker run ... stream-sentinel standalone-job --job-classname com.streamsentinel.flink.StreamSentinelJob
