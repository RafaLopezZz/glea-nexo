package com.glea.nexo.api.dto.inventory;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FarmCreateRequestDto(
        @NotBlank
        @Size(min = 1, max = 50)
        @Pattern(regexp = "^[a-z0-9-]+$", message = "code must match [a-z0-9-]+")
        @Schema(example = "finca1")
        String code,
        @NotBlank
        @Size(min = 1, max = 120)
        @Schema(example = "Finca Norte")
        String name,
        @Schema(description = "Optional location payload stored as JSON", example = "{\"lat\":37.35,\"lng\":-5.98}")
        JsonNode location
) {
}
