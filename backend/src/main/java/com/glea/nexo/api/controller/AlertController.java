package com.glea.nexo.api.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.glea.nexo.api.dto.alerts.AlertResponseDto;
import com.glea.nexo.api.error.ApiErrorResponse;
import com.glea.nexo.application.alerts.AlertQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts")
public class AlertController {

    private final AlertQueryService alertQueryService;

    public AlertController(AlertQueryService alertQueryService) {
        this.alertQueryService = alertQueryService;
    }

    @GetMapping
    @Operation(summary = "List operational alerts v1",
            description = "The query parameters from/to are optional. When both are present they must use ISO-8601 UTC timestamps, from must be before or equal to to, and the range must not exceed 2 years.")
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operational alerts list", content = @Content(examples = @ExampleObject(value = """
                    [{
                      "type":"STALE_DEVICE",
                      "severity":"WARN",
                      "alertTs":"2026-01-01T01:00:00Z",
                      "zoneId":"41ef1c42-d2c3-4b6f-a702-e891be507f42",
                      "deviceId":"8e722a06-6ec2-4c63-bc32-d81a24bba95f",
                      "deviceUid":"pi-gw-001",
                      "sensorId":"9471597a-7498-48f9-ae51-3686ddcca1ec",
                      "sensorUid":"soil-01",
                      "message":"sin telemetría reciente desde 2026-01-01T00:00:00Z"
                    }]
                    """))),
            @ApiResponse(responseCode = "400", description = "Invalid time range or parameter format", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<AlertResponseDto>> getAlerts(
            @Parameter(description = "Optional zone identifier used to scope the alert query", example = "4cf0f8c5-ef0c-4d1b-b857-1c0a5514a38a")
            @RequestParam(required = false) UUID zoneId,
            @Parameter(description = "Optional device identifier used to scope the alert query", example = "8e722a06-6ec2-4c63-bc32-d81a24bba95f")
            @RequestParam(required = false) UUID deviceId,
            @Parameter(description = "Inclusive event-time lower bound in ISO-8601 UTC. Optional.", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Inclusive event-time upper bound in ISO-8601 UTC. Optional. If both from and to are present, the range must not exceed 2 years.", example = "2026-12-31T23:59:59Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(alertQueryService.findAlerts(zoneId, deviceId, from, to));
    }
}
