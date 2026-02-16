package com.glea.nexo.api.dto.inventory;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record FarmResponseDto(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        JsonNode location,
        Instant createdAt,
        Instant updatedAt
) {
}
