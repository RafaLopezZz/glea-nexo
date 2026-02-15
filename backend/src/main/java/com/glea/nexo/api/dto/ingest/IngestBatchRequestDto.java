package com.glea.nexo.api.dto.ingest;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record IngestBatchRequestDto(
        String source,
        String topic,
        @NotEmpty List<@Valid IngestReadingDto> readings) {
}
