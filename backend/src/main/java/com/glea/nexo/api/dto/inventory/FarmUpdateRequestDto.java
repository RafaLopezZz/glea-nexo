package com.glea.nexo.api.dto.inventory;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FarmUpdateRequestDto(
        @Schema(description = "Farm code (lowercase, hyphen-separated)", example = "finca-norte")
        @NotBlank
        @Size(min = 1, max = 50)
        @Pattern(regexp = "^[a-z0-9-]+$", message = "code must match [a-z0-9-]+")
        String code,
        @Schema(description = "Human-readable farm name", example = "Finca Norte")
        @NotBlank
        @Size(min = 1, max = 120)
        String name,
        @Schema(description = "GeoJSON location", example = "{\"type\": \"Point\", \"coordinates\": [-63.0, -31.5]}")
        JsonNode location
) {
}
