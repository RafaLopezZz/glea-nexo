package com.glea.nexo.api.dto.inventory;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ZoneUpdateRequestDto(
        @Schema(description = "Zone code (lowercase, hyphen-separated)", example = "zona-a")
        @NotBlank
        @Size(min = 1, max = 50)
        @Pattern(regexp = "^[a-z0-9-]+$", message = "code must match [a-z0-9-]+")
        String code,
        @Schema(description = "Human-readable zone name", example = "Zona A")
        @NotBlank
        @Size(min = 1, max = 120)
        String name,
        @Schema(description = "GeoJSON geometry", example = "{\"type\": \"Polygon\", \"coordinates\": [...]}")
        JsonNode geometry
) {
}
