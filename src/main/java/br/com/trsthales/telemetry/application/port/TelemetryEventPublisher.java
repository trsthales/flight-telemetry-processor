package br.com.trsthales.telemetry.application.port;

import br.com.trsthales.telemetry.domain.TelemetryPacket;

public interface TelemetryEventPublisher {

    void publish(TelemetryPacket packet);
}