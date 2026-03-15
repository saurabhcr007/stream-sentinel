FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml ./
COPY core-engine/pom.xml core-engine/
COPY flink-job/pom.xml flink-job/

RUN mvn dependency:go-offline -B

COPY core-engine/src core-engine/src
COPY flink-job/src flink-job/src

RUN mvn clean package -DskipTests -B

FROM flink:1.18.1-java17

COPY --from=build /app/flink-job/target/flink-job-*.jar /opt/flink/usrlib/stream-sentinel.jar

COPY config/ /opt/flink/conf/stream-sentinel/

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
