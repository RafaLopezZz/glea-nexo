package com.glea.nexo.api.dto.inventory;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record ZoneResponseDto(
        UUID id,
        UUID farmId,
        String code,
        String name,
        JsonNode geometry,
        Instant createdAt,
        Instant updatedAt
) {
}
