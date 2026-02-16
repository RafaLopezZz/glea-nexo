package com.glea.nexo.api.dto.inventory;

import com.glea.nexo.domain.common.enums.OnlineState;

import jakarta.validation.constraints.Size;

public record DeviceUpdateRequestDto(

        @Size(max = 120)
        String name,
        OnlineState state
) {
}
