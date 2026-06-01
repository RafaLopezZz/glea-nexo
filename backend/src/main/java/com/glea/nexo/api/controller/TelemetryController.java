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

import com.glea.nexo.api.dto.telemetry.TelemetryLatestResponseDto;
import com.glea.nexo.api.dto.telemetry.TelemetryReadingResponseDto;
import com.glea.nexo.application.telemetry.TelemetryQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/telemetry")
@Tag(name = "Telemetry")
public class TelemetryController {

    private final TelemetryQueryService telemetryQueryService;

    public TelemetryController(TelemetryQueryService telemetryQueryService) {
        this.telemetryQueryService = telemetryQueryService;
    }

    @GetMapping("/readings")
    @Operation(summary = "List telemetry readings for chart/table v1")
    public ResponseEntity<List<TelemetryReadingResponseDto>> getReadings(
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(telemetryQueryService.findReadings(zoneId, deviceId, from, to));
    }

    @GetMapping("/latest")
    @Operation(summary = "Get latest telemetry snapshot per sensor")
    public ResponseEntity<List<TelemetryLatestResponseDto>> getLatest(
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(telemetryQueryService.findLatest(zoneId, deviceId, from, to));
    }
}
