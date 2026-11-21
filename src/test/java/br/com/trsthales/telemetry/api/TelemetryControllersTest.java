package br.com.trsthales.telemetry.api;

import br.com.trsthales.telemetry.application.TelemetryFastPathService;
import br.com.trsthales.telemetry.domain.AircraftVitalState;
import br.com.trsthales.telemetry.domain.PacketTimestampOutOfRangeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.KafkaException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TelemetryIngestionController.class, AircraftHealthController.class})
@Import(GlobalExceptionHandler.class)
class TelemetryControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelemetryFastPathService fastPathService;

    @Test
    void returns202WhenPacketIsAccepted() throws Exception {
        when(fastPathService.ingest(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packetId": "pkt-001",
                                  "aircraftId": "PR-XMA",
                                  "flightNumber": "AD4021",
                                  "recordedAt": "2026-11-21T16:30:00Z",
                                  "engineTempCelsius": 910.00,
                                  "fuelRemainingKg": 4300.00,
                                  "cabinPressurePsi": 11.40,
                                  "diagnostics": {
                                    "apuStatus": "OFF"
                                  }
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void returns400WhenPayloadValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packetId": "",
                                  "aircraftId": "INVALID@@@ID",
                                  "flightNumber": "AD4021",
                                  "recordedAt": "2026-11-21T16:30:00Z",
                                  "engineTempCelsius": 120.999,
                                  "fuelRemainingKg": -10.00,
                                  "cabinPressurePsi": 11.40
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.packetId").exists())
                .andExpect(jsonPath("$.errors.aircraftId").exists())
                .andExpect(jsonPath("$.errors.engineTempCelsius").exists())
                .andExpect(jsonPath("$.errors.fuelRemainingKg").exists());
    }

    @Test
    void returns400OnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returns422WhenPacketTimestampIsOutOfRange() throws Exception {
        Instant drifted = Instant.parse("2099-01-01T00:00:00Z");
        when(fastPathService.ingest(any())).thenThrow(
                new PacketTimestampOutOfRangeException(drifted, "Packet timestamp exceeds maximum allowed future clock drift of 30s")
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packetId": "pkt-2099",
                                  "aircraftId": "PR-XMA",
                                  "flightNumber": "AD4021",
                                  "recordedAt": "2099-01-01T00:00:00Z",
                                  "engineTempCelsius": 850.00,
                                  "fuelRemainingKg": 4300.00,
                                  "cabinPressurePsi": 11.40
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.recordedAt").value("2099-01-01T00:00:00Z"));
    }

    @Test
    void returns503WithRetryAfterWhenKafkaOrRedisFails() throws Exception {
        when(fastPathService.ingest(any())).thenThrow(new KafkaException("Broker unavailable"));

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packetId": "pkt-002",
                                  "aircraftId": "PR-XMA",
                                  "flightNumber": "AD4021",
                                  "recordedAt": "2026-11-21T16:30:00Z",
                                  "engineTempCelsius": 850.00,
                                  "fuelRemainingKg": 4300.00,
                                  "cabinPressurePsi": 11.40
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void returns200WithNoStoreCacheHeaderWhenAircraftStateExists() throws Exception {
        AircraftVitalState state = AircraftVitalState.of(
                "PR-XMA", "AD4021", Instant.parse("2026-11-21T16:30:00Z"),
                new BigDecimal("960.00"), new BigDecimal("4000.00"), new BigDecimal("11.30")
        );
        when(fastPathService.getLatestVitals("PR-XMA")).thenReturn(Optional.of(state));

        mockMvc.perform(get("/api/v1/aircraft/PR-XMA/vitals"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.aircraftId").value("PR-XMA"))
                .andExpect(jsonPath("$.criticalAlert").value(true));
    }

    @Test
    void returns404ProblemDetailWhenAircraftIsNotFound() throws Exception {
        when(fastPathService.getLatestVitals("PR-ZZZ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/aircraft/PR-ZZZ/vitals"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString("PR-ZZZ")));
    }

    @Test
    void returns400WhenAircraftIdInPathIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/aircraft/INVALID$$$/vitals"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.aircraftId").value("invalid aircraft registration format"));
    }
}