package com.glea.nexo.api.dto.alerts;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record AlertResponseDto(
        @Schema(example = "STALE_DEVICE")
        String type,
        @Schema(example = "WARN")
        String severity,
        @Schema(example = "2026-01-01T01:00:00Z")
        Instant alertTs,
        @Schema(example = "41ef1c42-d2c3-4b6f-a702-e891be507f42")
        UUID zoneId,
        @Schema(example = "8e722a06-6ec2-4c63-bc32-d81a24bba95f")
        UUID deviceId,
        @Schema(example = "pi-gw-001")
        String deviceUid,
        @Schema(example = "9471597a-7498-48f9-ae51-3686ddcca1ec")
        UUID sensorId,
        @Schema(example = "soil-01")
        String sensorUid,
        @Schema(example = "sin telemetría reciente desde 2026-01-01T00:00:00Z")
        String message
) {
}
