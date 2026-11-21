package br.com.trsthales.telemetry.application.port;

import br.com.trsthales.telemetry.domain.AircraftVitalState;

import java.util.Optional;

public interface AircraftVitalStateRepository {

    boolean saveIfNewer(AircraftVitalState state);

    Optional<AircraftVitalState> findLatestByAircraftId(String aircraftId);
}