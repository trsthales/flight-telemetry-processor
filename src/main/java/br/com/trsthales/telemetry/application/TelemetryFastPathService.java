package br.com.trsthales.telemetry.application;

import br.com.trsthales.telemetry.application.port.AircraftVitalStateRepository;
import br.com.trsthales.telemetry.application.port.PacketIdempotencyStore;
import br.com.trsthales.telemetry.application.port.TelemetryEventPublisher;
import br.com.trsthales.telemetry.domain.AircraftVitalState;
import br.com.trsthales.telemetry.domain.PacketTimestampOutOfRangeException;
import br.com.trsthales.telemetry.domain.TelemetryPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class TelemetryFastPathService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryFastPathService.class);
    static final Duration MAX_FUTURE_DRIFT = Duration.ofSeconds(30);
    static final Duration MAX_PACKET_AGE = Duration.ofHours(6);

    private final AircraftVitalStateRepository repository;
    private final PacketIdempotencyStore idempotencyStore;
    private final TelemetryEventPublisher eventPublisher;
    private final Clock clock;

    public TelemetryFastPathService(
            AircraftVitalStateRepository repository,
            PacketIdempotencyStore idempotencyStore,
            TelemetryEventPublisher eventPublisher,
            Clock clock
    ) {
        this.repository = repository;
        this.idempotencyStore = idempotencyStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public boolean ingest(TelemetryPacket packet) {
        AircraftVitalState state = packet.vitals();
        validateTimestampWindow(state.recordedAt());

        if (!idempotencyStore.tryAcquire(packet.packetId())) {
            log.debug("Duplicate packet {} ignored for aircraft {}", packet.packetId(), state.aircraftId());
            return false;
        }

        try {
            boolean updated = repository.saveIfNewer(state);
            if (!updated) {
                log.debug("Discarded out-of-order vital state for aircraft {} at {}",
                        state.aircraftId(), state.recordedAt());
            }
            eventPublisher.publish(packet);
            return updated;
        } catch (RuntimeException ex) {
            idempotencyStore.release(packet.packetId());
            throw ex;
        }
    }

    public Optional<AircraftVitalState> getLatestVitals(String aircraftId) {
        return repository.findLatestByAircraftId(aircraftId.trim().toUpperCase(Locale.ROOT));
    }

    private void validateTimestampWindow(Instant recordedAt) {
        Instant now = Instant.now(clock);
        if (recordedAt.isAfter(now.plus(MAX_FUTURE_DRIFT))) {
            throw new PacketTimestampOutOfRangeException(
                    recordedAt,
                    "Packet timestamp exceeds maximum allowed future clock drift of 30s"
            );
        }
        if (recordedAt.isBefore(now.minus(MAX_PACKET_AGE))) {
            throw new PacketTimestampOutOfRangeException(
                    recordedAt,
                    "Packet timestamp is older than the 6h retention window"
            );
        }
    }
}