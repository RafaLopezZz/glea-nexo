package com.glea.nexo.application.ingest;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.glea.nexo.api.dto.ingest.IngestBatchItemResponseDto;
import com.glea.nexo.api.dto.ingest.IngestBatchRequestDto;
import com.glea.nexo.api.dto.ingest.IngestBatchResponseDto;
import com.glea.nexo.api.dto.ingest.IngestReadingDto;
import com.glea.nexo.application.ingest.IngestItemProcessor.IngestItemResult;
import com.glea.nexo.application.ingest.IngestItemProcessor.ItemStatus;

@Service
public class IngestServiceImpl implements IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestServiceImpl.class);

    private final IngestItemProcessor itemProcessor;

    public IngestServiceImpl(IngestItemProcessor itemProcessor) {
        this.itemProcessor = itemProcessor;
    }

    @Override
    public IngestBatchResponseDto ingestBatch(IngestBatchRequestDto request) {
        if (request == null || CollectionUtils.isEmpty(request.readings())) {
            throw new IllegalArgumentException("readings must not be empty");
        }

        int processed = 0;
        int duplicates = 0;
        int errors = 0;
        List<IngestBatchItemResponseDto> items = new ArrayList<>();

        for (int i = 0; i < request.readings().size(); i++) {
            IngestReadingDto reading = request.readings().get(i);
            String messageId = reading != null ? reading.messageId() : null;

            try {
                IngestItemResult result = itemProcessor.process(request, reading, i);
                items.add(new IngestBatchItemResponseDto(
                        result.index(),
                        result.messageId(),
                        result.status().name(),
                        result.detail()));

                if (result.status() == ItemStatus.PROCESSED) {
                    processed++;
                } else if (result.status() == ItemStatus.DUPLICATE) {
                    duplicates++;
                } else {
                    errors++;
                }
            } catch (Exception ex) {
                log.warn("ingest error messageId={} reason={}", messageId, ex.getMessage());
                items.add(new IngestBatchItemResponseDto(i, messageId, ItemStatus.ERROR.name(), ex.getMessage()));
                errors++;
            }
        }

        return new IngestBatchResponseDto(request.readings().size(), processed, duplicates, errors, items);
    }
}
