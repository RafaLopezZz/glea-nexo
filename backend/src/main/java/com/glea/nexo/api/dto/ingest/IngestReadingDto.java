package com.glea.nexo.api.dto.ingest;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;

public record IngestReadingDto(
        @NotBlank String messageId,
        @NotBlank String deviceId,
        String topic,
        Instant ts,
        BigDecimal value,
        String unit,
        Integer rssi,
        BigDecimal battery,
        JsonNode rawPayload) {
}
