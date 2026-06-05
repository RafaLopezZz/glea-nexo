package com.glea.nexo.api.error;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiErrorResponse(
        @Schema(example = "2026-01-01T00:00:00Z")
        Instant timestamp,
        @Schema(example = "400")
        int status,
        @Schema(example = "VALIDATION_ERROR")
        String error,
        @Schema(example = "Request validation failed")
        String message,
        @Schema(example = "/api/telemetry/readings")
        String path,
        @Schema(example = "d7ad6399-5f55-4b5d-890e-10859738f93f")
        String correlationId,
        @Schema(description = "Field-level validation errors when available")
        List<ApiFieldError> fieldErrors
) {
}
