package com.glea.nexo.api.dto.ingest;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record IngestReadingDto(
        @NotBlank
        String messageId,
        @NotBlank
        String deviceId,
        String topic,
        @NotNull(message = "ts is required")
        @Schema(description = "Event time of the measurement in UTC", example = "2026-01-01T00:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant ts,
        BigDecimal value,
        String unit,
        Integer rssi,
        BigDecimal battery,
        JsonNode rawPayload) {

}
