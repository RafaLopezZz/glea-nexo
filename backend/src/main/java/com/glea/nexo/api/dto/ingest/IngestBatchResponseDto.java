package com.glea.nexo.api.dto.ingest;

import java.util.List;

public record IngestBatchResponseDto(
        int total,
        int processed,
        int duplicates,
        int errors,
        List<IngestBatchItemResponseDto> items) {
}
