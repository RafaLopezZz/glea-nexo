package com.glea.nexo.api.dto.ingest;

public record IngestBatchItemResponseDto(
        int index,
        String messageId,
        String status,
        String detail) {
}
