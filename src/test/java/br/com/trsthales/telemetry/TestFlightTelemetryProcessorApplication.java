package br.com.trsthales.telemetry;

import org.springframework.boot.SpringApplication;

public class TestFlightTelemetryProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlightTelemetryProcessorApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
