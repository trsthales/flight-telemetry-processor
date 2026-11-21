package br.com.trsthales.telemetry.application;

import br.com.trsthales.telemetry.application.port.AircraftVitalStateRepository;
import br.com.trsthales.telemetry.application.port.PacketIdempotencyStore;
import br.com.trsthales.telemetry.application.port.TelemetryEventPublisher;
import br.com.trsthales.telemetry.domain.AircraftVitalState;
import br.com.trsthales.telemetry.domain.PacketTimestampOutOfRangeException;
import br.com.trsthales.telemetry.domain.TelemetryPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryFastPathServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T17:00:00Z");

    @Mock
    private AircraftVitalStateRepository repository;

    @Mock
    private PacketIdempotencyStore idempotencyStore;

    @Mock
    private TelemetryEventPublisher eventPublisher;

    private TelemetryFastPathService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryFastPathService(
                repository,
                idempotencyStore,
                eventPublisher,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void ingestsValidNewPacketIntoRedisAndKafka() {
        TelemetryPacket packet = packetAt("pkt-01", FIXED_NOW);
        when(idempotencyStore.tryAcquire("pkt-01")).thenReturn(true);
        when(repository.saveIfNewer(packet.vitals())).thenReturn(true);

        assertThat(service.ingest(packet)).isTrue();

        verify(repository).saveIfNewer(packet.vitals());
        verify(eventPublisher).publish(packet);
        verify(idempotencyStore, never()).release(any());
    }

    @Test
    void ignoresDuplicatePacketWithoutTouchingStateOrKafka() {
        TelemetryPacket duplicate = packetAt("pkt-dup", FIXED_NOW);
        when(idempotencyStore.tryAcquire("pkt-dup")).thenReturn(false);

        assertThat(service.ingest(duplicate)).isFalse();

        verifyNoInteractions(repository, eventPublisher);
    }

    @Test
    void publishesOutOfOrderPacketToKafkaEvenWhenVitalStateIsNotUpdated() {
        TelemetryPacket delayed = packetAt("pkt-delayed", FIXED_NOW.minusSeconds(120));
        when(idempotencyStore.tryAcquire("pkt-delayed")).thenReturn(true);
        when(repository.saveIfNewer(delayed.vitals())).thenReturn(false);

        assertThat(service.ingest(delayed)).isFalse();

        verify(eventPublisher).publish(delayed);
    }

    @Test
    void releasesIdempotencyKeyWhenKafkaPublishFails() {
        TelemetryPacket packet = packetAt("pkt-fail", FIXED_NOW);
        when(idempotencyStore.tryAcquire("pkt-fail")).thenReturn(true);
        when(repository.saveIfNewer(packet.vitals())).thenReturn(true);
        doThrow(new RuntimeException("broker down")).when(eventPublisher).publish(packet);

        assertThatThrownBy(() -> service.ingest(packet))
                .isInstanceOf(RuntimeException.class);

        verify(idempotencyStore).release("pkt-fail");
    }

    @Test
    void rejectsOutOfWindowTimestampsBeforeCheckingIdempotency() {
        TelemetryPacket futurePacket = packetAt("pkt-future", FIXED_NOW.plusSeconds(31));
        TelemetryPacket expiredPacket = packetAt("pkt-old", FIXED_NOW.minusSeconds(6 * 3600 + 1));

        assertThatThrownBy(() -> service.ingest(futurePacket))
                .isInstanceOf(PacketTimestampOutOfRangeException.class);
        assertThatThrownBy(() -> service.ingest(expiredPacket))
                .isInstanceOf(PacketTimestampOutOfRangeException.class);

        verifyNoInteractions(idempotencyStore, repository, eventPublisher);
    }

    @Test
    void normalizesAircraftIdOnLookup() {
        when(repository.findLatestByAircraftId("PR-XMA")).thenReturn(Optional.empty());

        assertThat(service.getLatestVitals(" pr-xma ")).isEmpty();
        verify(repository).findLatestByAircraftId("PR-XMA");
    }

    private TelemetryPacket packetAt(String packetId, Instant recordedAt) {
        AircraftVitalState vitals = AircraftVitalState.of(
                "PR-XMA",
                "AD4021",
                recordedAt,
                new BigDecimal("850.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("11.30")
        );
        return new TelemetryPacket(packetId, vitals, Map.of("apuStatus", "OFF"));
    }
}