package com.glea.nexo.api.dto.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryLatestResponseDto(
        UUID zoneId,
        UUID deviceId,
        String deviceUid,
        String deviceState,
        BigDecimal deviceBattery,
        Integer deviceRssi,
        UUID sensorId,
        String sensorUid,
        String sensorType,
        Instant lastTs,
        BigDecimal value,
        String unit,
        String quality
) {
}
