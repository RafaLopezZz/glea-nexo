package com.glea.nexo.application.ingest;

import com.glea.nexo.api.dto.ingest.IngestBatchRequestDto;
import com.glea.nexo.api.dto.ingest.IngestBatchResponseDto;

public interface IngestService {

    IngestBatchResponseDto ingestBatch(IngestBatchRequestDto request);
}
