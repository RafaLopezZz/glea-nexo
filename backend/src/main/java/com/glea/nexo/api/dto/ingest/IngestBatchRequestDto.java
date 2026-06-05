package com.glea.nexo.api.dto.ingest;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record IngestBatchRequestDto(
        @Schema(description = "Logical source of the batch", example = "manual-dedupe-test-rpi")
        String source,
        @Schema(description = "MQTT topic applied to all readings when a per-reading topic is not provided", example = "agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry")
        String topic,
        @Schema(description = "Readings included in the batch", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty List<@Valid IngestReadingDto> readings) {
}
