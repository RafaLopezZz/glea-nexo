package com.glea.nexo.api.dto.inventory;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public record ZoneResponseDto(
        @Schema(example = "41ef1c42-d2c3-4b6f-a702-e891be507f42")
        UUID id,
        @Schema(example = "d4620d12-97aa-49c4-afde-8cbcbf8472de")
        UUID farmId,
        @Schema(example = "zona1")
        String code,
        @Schema(example = "Zona Norte")
        String name,
        @Schema(example = "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}" )
        JsonNode geometry,
        @Schema(example = "2026-01-01T00:00:00Z")
        Instant createdAt,
        @Schema(example = "2026-01-01T00:00:00Z")
        Instant updatedAt
) {
}
