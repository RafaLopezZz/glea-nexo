package com.glea.nexo.api.dto.inventory;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public record FarmResponseDto(
        @Schema(example = "d4620d12-97aa-49c4-afde-8cbcbf8472de")
        UUID id,
        @Schema(example = "7cc0f2d0-30f6-4d3c-b48e-1888d4396888")
        UUID organizationId,
        @Schema(example = "finca1")
        String code,
        @Schema(example = "Finca Norte")
        String name,
        @Schema(example = "{\"lat\":37.35,\"lng\":-5.98}")
        JsonNode location,
        @Schema(example = "2026-01-01T00:00:00Z")
        Instant createdAt,
        @Schema(example = "2026-01-01T00:00:00Z")
        Instant updatedAt
) {
}
