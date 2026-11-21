package br.com.trsthales.telemetry.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String RAW_TELEMETRY_TOPIC = "telemetry.raw";
    public static final String DLQ_TELEMETRY_TOPIC = "telemetry.dlq";

    @Bean
    public NewTopic rawTelemetryTopic() {
        return TopicBuilder.name(RAW_TELEMETRY_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic dlqTelemetryTopic() {
        return TopicBuilder.name(DLQ_TELEMETRY_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}