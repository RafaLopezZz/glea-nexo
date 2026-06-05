package com.glea.nexo.api.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.glea.nexo.domain.common.enums.OnlineState;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceResponseDto(
        @Schema(example = "8e722a06-6ec2-4c63-bc32-d81a24bba95f")
        UUID id,
        @Schema(example = "7cc0f2d0-30f6-4d3c-b48e-1888d4396888")
        UUID organizationId,
        @Schema(example = "d4620d12-97aa-49c4-afde-8cbcbf8472de")
        UUID farmId,
        @Schema(example = "41ef1c42-d2c3-4b6f-a702-e891be507f42")
        UUID zoneId,
        @Schema(example = "pi-gw-001")
        String deviceUid,
        @Schema(example = "Gateway parcela norte")
        String name,
        @Schema(example = "ONLINE")
        OnlineState state,
        @Schema(example = "2026-01-01T00:00:00Z")
        Instant lastSeenAt,
        @Schema(example = "-55")
        Integer lastRssi,
        @Schema(example = "3.78")
        BigDecimal lastBatteryV,
        @Schema(example = "2026-01-01T00:00:00Z")
        Instant createdAt,
        @Schema(example = "2026-01-01T00:00:00Z")
        Instant updatedAt
) {
}
