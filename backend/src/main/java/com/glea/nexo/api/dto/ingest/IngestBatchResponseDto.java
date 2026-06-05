package com.glea.nexo.api.dto.ingest;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record IngestBatchResponseDto(
        @Schema(description = "Total items received in the batch", example = "1")
        int total,
        @Schema(description = "Items successfully processed", example = "1")
        int processed,
        @Schema(description = "Items treated as duplicates by idempotency checks", example = "0")
        int duplicates,
        @Schema(description = "Items rejected with processing errors", example = "0")
        int errors,
        @Schema(description = "Per-item processing outcome")
        List<IngestBatchItemResponseDto> items) {
}
