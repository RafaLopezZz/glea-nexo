package com.glea.nexo.api.error;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiFieldError(
        @Schema(example = "readings[0].ts")
        String field,
        @Schema(example = "ts is required")
        String message
) {
}
