CREATE TABLE telemetry_history (
                                   id BIGSERIAL PRIMARY KEY,
                                   packet_id VARCHAR(64) NOT NULL UNIQUE,
                                   aircraft_id VARCHAR(16) NOT NULL,
                                   flight_number VARCHAR(16) NOT NULL,
                                   recorded_at TIMESTAMPTZ NOT NULL,
                                   engine_temp_celsius NUMERIC(6, 2) NOT NULL,
                                   fuel_remaining_kg NUMERIC(10, 2) NOT NULL,
                                   cabin_pressure_psi NUMERIC(5, 2) NOT NULL,
                                   has_critical_alert BOOLEAN NOT NULL DEFAULT FALSE,
                                   raw_payload JSONB NOT NULL,
                                   ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_telemetry_aircraft_recorded
    ON telemetry_history (aircraft_id, recorded_at DESC);