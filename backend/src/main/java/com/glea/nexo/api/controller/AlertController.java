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
import com.glea.nexo.application.alerts.AlertQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    public ResponseEntity<List<AlertResponseDto>> getAlerts(
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID deviceId,
            @Parameter(description = "Inclusive event-time lower bound in ISO-8601 UTC. Optional.", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Inclusive event-time upper bound in ISO-8601 UTC. Optional. If both from and to are present, the range must not exceed 2 years.", example = "2026-12-31T23:59:59Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(alertQueryService.findAlerts(zoneId, deviceId, from, to));
    }
}
