package br.com.trsthales.telemetry.api;

import br.com.trsthales.telemetry.api.dto.AircraftVitalResponse;
import br.com.trsthales.telemetry.application.TelemetryFastPathService;
import br.com.trsthales.telemetry.domain.AircraftVitalState;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/aircraft")
public class AircraftHealthController {

    private final TelemetryFastPathService fastPathService;

    public AircraftHealthController(TelemetryFastPathService fastPathService) {
        this.fastPathService = fastPathService;
    }

    @GetMapping("/{aircraftId}/vitals")
    public ResponseEntity<AircraftVitalResponse> getVitals(
            @PathVariable
            @Pattern(regexp = AircraftVitalState.REGISTRATION_PATTERN, message = "invalid aircraft registration format")
            String aircraftId
    ) {
        return fastPathService.getLatestVitals(aircraftId)
                .map(AircraftVitalResponse::fromDomain)
                .map(body -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(body))
                .orElseThrow(() -> new ErrorResponseException(
                        HttpStatus.NOT_FOUND,
                        ProblemDetail.forStatusAndDetail(
                                HttpStatus.NOT_FOUND,
                                "No active vital state found for aircraft " + aircraftId.toUpperCase(Locale.ROOT)
                        ),
                        null
                ));
    }
}