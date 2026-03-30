package com.glea.nexo.api.dto.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryReadingResponseDto(
        UUID readingId,
        Instant ts,
        UUID zoneId,
        UUID deviceId,
        String deviceUid,
        UUID sensorId,
        String sensorUid,
        String sensorType,
        BigDecimal value,
        String unit,
        String quality,
        BigDecimal battery,
        Integer rssi
) {
}
