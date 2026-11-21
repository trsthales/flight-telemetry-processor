package br.com.trsthales.telemetry.infrastructure.kafka;

import br.com.trsthales.telemetry.application.port.TelemetryEventPublisher;
import br.com.trsthales.telemetry.domain.AircraftVitalState;
import br.com.trsthales.telemetry.domain.TelemetryPacket;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaTelemetryEventPublisher implements TelemetryEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 3L;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaTelemetryEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(TelemetryPacket packet) {
        AircraftVitalState vitals = packet.vitals();
        RawTelemetryMessage message = new RawTelemetryMessage(
                packet.packetId(),
                vitals.aircraftId(),
                vitals.flightNumber(),
                vitals.recordedAt(),
                vitals.engineTempCelsius(),
                vitals.fuelRemainingKg(),
                vitals.cabinPressurePsi(),
                vitals.criticalAlert(),
                packet.diagnostics()
        );

        try {
            kafkaTemplate.send(KafkaConfig.RAW_TELEMETRY_TOPIC, vitals.aircraftId(), message)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KafkaException("Interrupted while publishing telemetry packet " + packet.packetId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new KafkaException("Failed to publish telemetry packet " + packet.packetId(), ex);
        }
    }

    public record RawTelemetryMessage(
            String packetId,
            String aircraftId,
            String flightNumber,
            Instant recordedAt,
            BigDecimal engineTempCelsius,
            BigDecimal fuelRemainingKg,
            BigDecimal cabinPressurePsi,
            boolean criticalAlert,
            Map<String, Object> diagnostics
    ) {
    }
}