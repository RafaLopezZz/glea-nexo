package com.glea.nexo.api.dto.ingest;

import io.swagger.v3.oas.annotations.media.Schema;

public record IngestBatchItemResponseDto(
        @Schema(example = "0")
        int index,
        @Schema(example = "dedupe-001")
        String messageId,
        @Schema(description = "Per-item outcome", example = "PROCESSED")
        String status,
        @Schema(example = "telemetry reading persisted")
        String detail) {
}
