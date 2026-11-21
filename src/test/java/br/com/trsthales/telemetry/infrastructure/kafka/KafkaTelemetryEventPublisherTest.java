package br.com.trsthales.telemetry.infrastructure.kafka;

import br.com.trsthales.telemetry.domain.AircraftVitalState;
import br.com.trsthales.telemetry.domain.TelemetryPacket;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        KafkaConfig.class,
        KafkaTelemetryEventPublisher.class,
        KafkaAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@Testcontainers
class KafkaTelemetryEventPublisherTest {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @Autowired
    private KafkaTelemetryEventPublisher publisher;

    @Test
    void publishesRawPacketKeyedByAircraftId() {
        Map<String, Object> diagnostics = new HashMap<>();
        diagnostics.put("apuStatus", "OFF");
        diagnostics.put("optionalSensor", null);

        AircraftVitalState vitals = AircraftVitalState.of(
                "pr-xma",
                "ad4021",
                Instant.parse("2026-11-21T16:30:00Z"),
                new BigDecimal("965.00"),
                new BigDecimal("4100.00"),
                new BigDecimal("11.30")
        );
        TelemetryPacket packet = new TelemetryPacket("pkt-kafka-01", vitals, diagnostics);

        publisher.publish(packet);

        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "publisher-test-consumer",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(KafkaConfig.RAW_TELEMETRY_TOPIC));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            assertThat(records.count()).isEqualTo(1);
            ConsumerRecord<String, String> record = records.iterator().next();
            assertThat(record.key()).isEqualTo("PR-XMA");
            assertThat(record.value())
                    .contains("\"packetId\":\"pkt-kafka-01\"")
                    .contains("\"aircraftId\":\"PR-XMA\"")
                    .contains("\"criticalAlert\":true")
                    .contains("\"apuStatus\":\"OFF\"");
        }
    }
}