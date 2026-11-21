package br.com.trsthales.telemetry.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AircraftVitalStateTest {

    @Test
    void safeLimitsDoNotTriggerCriticalAlert() {
        AircraftVitalState state = buildState("950.00", "1200.00", "10.50");

        assertThat(state.aircraftId()).isEqualTo("PR-XMA");
        assertThat(state.flightNumber()).isEqualTo("AD4021");
        assertThat(state.criticalAlert()).isFalse();
    }

    @Test
    void engineTempAboveThresholdFlagsCriticalAlert() {
        assertThat(buildState("950.01", "3500.00", "11.20").criticalAlert()).isTrue();
    }

    @Test
    void fuelBelowReserveFlagsCriticalAlert() {
        assertThat(buildState("820.00", "1199.99", "11.20").criticalAlert()).isTrue();
    }

    @Test
    void cabinPressureBelowMinimumFlagsCriticalAlert() {
        assertThat(buildState("820.00", "3500.00", "10.49").criticalAlert()).isTrue();
    }

    @Test
    void normalizesIdentifiersAndRecalculatesAlertInConstructor() {
        AircraftVitalState state = new AircraftVitalState(
                " pr-xma ",
                " ad4021 ",
                Instant.parse("2026-11-21T16:30:00Z"),
                new BigDecimal("990.00"),
                new BigDecimal("3500.00"),
                new BigDecimal("11.20"),
                false
        );

        assertThat(state.aircraftId()).isEqualTo("PR-XMA");
        assertThat(state.flightNumber()).isEqualTo("AD4021");
        assertThat(state.criticalAlert()).isTrue();
    }

    private AircraftVitalState buildState(String temp, String fuel, String pressure) {
        return AircraftVitalState.of(
                " pr-xma ",
                " ad4021 ",
                Instant.parse("2026-11-21T16:30:00Z"),
                new BigDecimal(temp),
                new BigDecimal(fuel),
                new BigDecimal(pressure)
        );
    }
}