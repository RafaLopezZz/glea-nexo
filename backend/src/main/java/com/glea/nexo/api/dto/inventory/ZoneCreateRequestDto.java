package com.glea.nexo.api.dto.inventory;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ZoneCreateRequestDto(
        @NotBlank
        @Size(min = 1, max = 50)
        @Pattern(regexp = "^[a-z0-9-]+$", message = "code must match [a-z0-9-]+")
        String code,
        @NotBlank
        @Size(min = 1, max = 120)
        String name,
        JsonNode geometry
) {
}
