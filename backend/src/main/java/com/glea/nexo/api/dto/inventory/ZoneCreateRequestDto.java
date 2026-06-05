package com.glea.nexo.api.dto.inventory;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ZoneCreateRequestDto(
        @NotBlank
        @Size(min = 1, max = 50)
        @Pattern(regexp = "^[a-z0-9-]+$", message = "code must match [a-z0-9-]+")
        @Schema(example = "zona1")
        String code,
        @NotBlank
        @Size(min = 1, max = 120)
        @Schema(example = "Zona Norte")
        String name,
        @Schema(description = "Optional geometry payload stored as JSON", example = "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}" )
        JsonNode geometry
) {
}
