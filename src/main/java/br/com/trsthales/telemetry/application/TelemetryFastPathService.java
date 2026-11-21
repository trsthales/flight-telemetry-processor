package br.com.trsthales.telemetry.application;

import br.com.trsthales.telemetry.application.port.AircraftVitalStateRepository;
import br.com.trsthales.telemetry.domain.AircraftVitalState;
import br.com.trsthales.telemetry.domain.PacketTimestampOutOfRangeException;
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
    private final Clock clock;

    public TelemetryFastPathService(AircraftVitalStateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean ingest(AircraftVitalState state) {
        Instant now = Instant.now(clock);
        Instant recordedAt = state.recordedAt();

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

        boolean updated = repository.saveIfNewer(state);
        if (!updated) {
            log.debug("Discarded out-of-order or same-millisecond vital state for aircraft {} at {}",
                    state.aircraftId(), recordedAt);
        }
        return updated;
    }

    public Optional<AircraftVitalState> getLatestVitals(String aircraftId) {
        return repository.findLatestByAircraftId(aircraftId.trim().toUpperCase(Locale.ROOT));
    }
}