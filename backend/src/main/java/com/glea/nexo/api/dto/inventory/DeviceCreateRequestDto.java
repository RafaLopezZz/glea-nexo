package com.glea.nexo.api.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCreateRequestDto(
        @NotBlank
        @Size(min = 1, max = 80)
        String deviceUid,
        @Size(max = 120)
        String name
) {
}
