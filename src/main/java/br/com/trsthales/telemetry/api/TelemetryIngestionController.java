package br.com.trsthales.telemetry.api;

import br.com.trsthales.telemetry.api.dto.TelemetryPacketRequest;
import br.com.trsthales.telemetry.application.TelemetryFastPathService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryIngestionController {

    private final TelemetryFastPathService fastPathService;

    public TelemetryIngestionController(TelemetryFastPathService fastPathService) {
        this.fastPathService = fastPathService;
    }

    @PostMapping
    public ResponseEntity<Void> receivePacket(@Valid @RequestBody TelemetryPacketRequest request) {
        fastPathService.ingest(request.toPacket());
        return ResponseEntity.accepted().build();
    }
}