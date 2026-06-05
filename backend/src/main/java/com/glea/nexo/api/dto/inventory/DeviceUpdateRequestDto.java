package com.glea.nexo.api.dto.inventory;

import com.glea.nexo.domain.common.enums.OnlineState;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record DeviceUpdateRequestDto(

        @Size(max = 120)
        @Schema(example = "Gateway parcela norte")
        String name,
        @Schema(example = "ONLINE")
        OnlineState state
) {}
