package br.com.trsthales.telemetry.api.dto;

import br.com.trsthales.telemetry.domain.AircraftVitalState;
import br.com.trsthales.telemetry.domain.TelemetryPacket;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record TelemetryPacketRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,64}$", message = "invalid packet identifier format")
        String packetId,

        @NotBlank
        @Pattern(regexp = AircraftVitalState.REGISTRATION_PATTERN, message = "invalid aircraft registration format")
        String aircraftId,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9]{2,10}$", message = "invalid flight number format")
        String flightNumber,

        @NotNull
        Instant recordedAt,

        @NotNull
        @DecimalMin("-80.0") @DecimalMax("2000.0")
        @Digits(integer = 4, fraction = 2)
        BigDecimal engineTempCelsius,

        @NotNull
        @PositiveOrZero @DecimalMax("400000.0")
        @Digits(integer = 6, fraction = 2)
        BigDecimal fuelRemainingKg,

        @NotNull
        @DecimalMin("0.0") @DecimalMax("25.0")
        @Digits(integer = 2, fraction = 2)
        BigDecimal cabinPressurePsi,

        @Size(max = 32)
        Map<String, Object> diagnostics
) {
    public TelemetryPacket toPacket() {
        AircraftVitalState vitals = AircraftVitalState.of(
                aircraftId,
                flightNumber,
                recordedAt,
                engineTempCelsius,
                fuelRemainingKg,
                cabinPressurePsi
        );
        return new TelemetryPacket(packetId, vitals, diagnostics);
    }
}