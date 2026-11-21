package br.com.trsthales.telemetry.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TelemetryPacket(
        String packetId,
        AircraftVitalState vitals,
        Map<String, Object> diagnostics
) {
    public TelemetryPacket {
        Objects.requireNonNull(packetId, "packetId must not be null");
        Objects.requireNonNull(vitals, "vitals must not be null");

        packetId = packetId.trim();
        if (packetId.isEmpty()) {
            throw new IllegalArgumentException("packetId must not be blank");
        }
        diagnostics = diagnostics == null || diagnostics.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
    }
}