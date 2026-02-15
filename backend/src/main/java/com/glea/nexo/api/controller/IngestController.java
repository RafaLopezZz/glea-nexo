package com.glea.nexo.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.glea.nexo.api.dto.ingest.IngestBatchRequestDto;
import com.glea.nexo.api.dto.ingest.IngestBatchResponseDto;
import com.glea.nexo.application.ingest.IngestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ingest/readings")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/batch")
    public ResponseEntity<IngestBatchResponseDto> ingestBatch(@Valid @RequestBody IngestBatchRequestDto request) {
        return ResponseEntity.ok(ingestService.ingestBatch(request));
    }
}
