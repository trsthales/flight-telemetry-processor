package br.com.trsthales.telemetry.domain;

import java.time.Instant;

public class PacketTimestampOutOfRangeException extends RuntimeException {
    private final Instant recordedAt;

    public PacketTimestampOutOfRangeException(Instant recordedAt, String reason) {
        super(reason);
        this.recordedAt = recordedAt;
    }

    public Instant recordedAt() {
        return recordedAt;
    }
}
