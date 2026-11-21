package br.com.trsthales.telemetry.api.dto;

import br.com.trsthales.telemetry.domain.AircraftVitalState;

import java.math.BigDecimal;
import java.time.Instant;

public record AircraftVitalResponse(
        String aircraftId,
        String flightNumber,
        Instant recordedAt,
        BigDecimal engineTempCelsius,
        BigDecimal fuelRemainingKg,
        BigDecimal cabinPressurePsi,
        boolean criticalAlert
) {
    public static AircraftVitalResponse fromDomain(AircraftVitalState state) {
        return new AircraftVitalResponse(
                state.aircraftId(),
                state.flightNumber(),
                state.recordedAt(),
                state.engineTempCelsius(),
                state.fuelRemainingKg(),
                state.cabinPressurePsi(),
                state.criticalAlert()
        );
    }
}