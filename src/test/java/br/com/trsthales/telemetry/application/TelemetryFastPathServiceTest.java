package br.com.trsthales.telemetry.application;

import br.com.trsthales.telemetry.application.port.AircraftVitalStateRepository;
import br.com.trsthales.telemetry.domain.AircraftVitalState;
import br.com.trsthales.telemetry.domain.PacketTimestampOutOfRangeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryFastPathServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T17:00:00Z");

    @Mock
    private AircraftVitalStateRepository repository;

    private TelemetryFastPathService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryFastPathService(repository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsTimestampsAtExactDriftAndRetentionBoundaries() {
        when(repository.saveIfNewer(any())).thenReturn(true);

        AircraftVitalState upperBound = stateAt(FIXED_NOW.plusSeconds(30));
        AircraftVitalState lowerBound = stateAt(FIXED_NOW.minusSeconds(6 * 3600));

        assertThat(service.ingest(lowerBound)).isTrue();
        assertThat(service.ingest(upperBound)).isTrue();
        verify(repository, times(2)).saveIfNewer(any());
    }

    @Test
    void rejectsFutureDriftAbove30Seconds() {
        AircraftVitalState drifted = stateAt(FIXED_NOW.plusSeconds(31));

        assertThatThrownBy(() -> service.ingest(drifted))
                .isInstanceOf(PacketTimestampOutOfRangeException.class)
                .hasMessageContaining("30s");

        verify(repository, never()).saveIfNewer(any());
    }

    @Test
    void rejectsPacketsOlderThanSixHours() {
        AircraftVitalState expired = stateAt(FIXED_NOW.minusSeconds(6 * 3600 + 1));

        assertThatThrownBy(() -> service.ingest(expired))
                .isInstanceOf(PacketTimestampOutOfRangeException.class);

        verify(repository, never()).saveIfNewer(any());
    }

    @Test
    void returnsFalseWhenRepositoryDiscardsStalePacket() {
        AircraftVitalState packet = stateAt(FIXED_NOW);
        when(repository.saveIfNewer(packet)).thenReturn(false);

        assertThat(service.ingest(packet)).isFalse();
        verify(repository).saveIfNewer(packet);
    }

    @Test
    void normalizesAircraftIdOnLookup() {
        when(repository.findLatestByAircraftId("PR-XMA")).thenReturn(Optional.empty());

        assertThat(service.getLatestVitals(" pr-xma ")).isEmpty();
        verify(repository).findLatestByAircraftId("PR-XMA");
    }

    private AircraftVitalState stateAt(Instant recordedAt) {
        return AircraftVitalState.of(
                "PR-XMA",
                "AD4021",
                recordedAt,
                new BigDecimal("850.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("11.30")
        );
    }
}