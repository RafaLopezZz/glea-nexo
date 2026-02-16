package com.glea.nexo.api.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.glea.nexo.domain.common.enums.OnlineState;

public record DeviceResponseDto(
        UUID id,
        UUID organizationId,
        UUID farmId,
        UUID zoneId,
        String deviceUid,
        String name,
        OnlineState state,
        Instant lastSeenAt,
        Integer lastRssi,
        BigDecimal lastBatteryV,
        Instant createdAt,
        Instant updatedAt
) {
}
