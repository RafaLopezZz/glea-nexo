package com.glea.nexo.api.dto.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCreateRequestDto(
        @NotBlank
        @Size(min = 1, max = 80)
        @Schema(example = "pi-gw-001")
        String deviceUid,
        @Size(max = 120)
        @Schema(example = "Gateway parcela norte")
        String name
) {
}
