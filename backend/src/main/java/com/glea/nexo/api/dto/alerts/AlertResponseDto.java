package com.glea.nexo.api.dto.alerts;

import java.time.Instant;
import java.util.UUID;

public record AlertResponseDto(
        String type,
        String severity,
        Instant alertTs,
        UUID zoneId,
        UUID deviceId,
        String deviceUid,
        UUID sensorId,
        String sensorUid,
        String message
) {
}
