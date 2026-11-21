package br.com.trsthales.telemetry.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record AircraftVitalState(
        String aircraftId,
        String flightNumber,
        Instant recordedAt,
        BigDecimal engineTempCelsius,
        BigDecimal fuelRemainingKg,
        BigDecimal cabinPressurePsi,
        boolean criticalAlert
) {
    public static final String REGISTRATION_PATTERN = "^[A-Za-z0-9-]{3,16}$";

    private static final BigDecimal MAX_SAFE_ENGINE_TEMP = new BigDecimal("950.00");
    private static final BigDecimal MIN_SAFE_CABIN_PRESSURE = new BigDecimal("10.50");
    private static final BigDecimal MIN_RESERVE_FUEL_KG = new BigDecimal("1200.00");

    public AircraftVitalState {
        Objects.requireNonNull(aircraftId, "aircraftId must not be null");
        Objects.requireNonNull(flightNumber, "flightNumber must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        Objects.requireNonNull(engineTempCelsius, "engineTempCelsius must not be null");
        Objects.requireNonNull(fuelRemainingKg, "fuelRemainingKg must not be null");
        Objects.requireNonNull(cabinPressurePsi, "cabinPressurePsi must not be null");

        aircraftId = aircraftId.trim().toUpperCase(Locale.ROOT);
        flightNumber = flightNumber.trim().toUpperCase(Locale.ROOT);
        criticalAlert = isCritical(engineTempCelsius, fuelRemainingKg, cabinPressurePsi);
    }

    public static AircraftVitalState of(
            String aircraftId,
            String flightNumber,
            Instant recordedAt,
            BigDecimal engineTempCelsius,
            BigDecimal fuelRemainingKg,
            BigDecimal cabinPressurePsi
    ) {
        return new AircraftVitalState(
                aircraftId,
                flightNumber,
                recordedAt,
                engineTempCelsius,
                fuelRemainingKg,
                cabinPressurePsi,
                false
        );
    }

    private static boolean isCritical(
            BigDecimal engineTempCelsius,
            BigDecimal fuelRemainingKg,
            BigDecimal cabinPressurePsi
    ) {
        return engineTempCelsius.compareTo(MAX_SAFE_ENGINE_TEMP) > 0
                || cabinPressurePsi.compareTo(MIN_SAFE_CABIN_PRESSURE) < 0
                || fuelRemainingKg.compareTo(MIN_RESERVE_FUEL_KG) < 0;
    }
}