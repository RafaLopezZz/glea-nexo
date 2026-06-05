package com.glea.nexo.api.dto.ingest;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record IngestReadingDto(
        @NotBlank
        @Schema(description = "Idempotency key emitted by the producer for this reading", example = "dedupe-001")
        String messageId,
        @NotBlank
        @Schema(description = "Gateway identifier or legacy gateway:sensor pair used by ingest context", example = "pi-gw-001:soil-01")
        String deviceId,
        @Schema(description = "Optional per-reading MQTT topic. If omitted, batch-level topic is used.", example = "agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry")
        String topic,
        @NotNull(message = "ts is required")
        @Schema(description = "Event time of the measurement in UTC", example = "2026-01-01T00:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant ts,
        @Schema(description = "Numeric reading value", example = "25.0")
        BigDecimal value,
        @Schema(description = "Engineering unit for the value", example = "%VWC")
        String unit,
        @Schema(description = "RSSI reported by the edge/gateway", example = "-55")
        Integer rssi,
        @Schema(description = "Battery voltage reported by the edge/gateway", example = "3.78")
        BigDecimal battery,
        @Schema(description = "Optional raw payload snapshot preserved for auditing")
        JsonNode rawPayload) {

}
