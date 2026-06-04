package com.glea.nexo.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.glea.nexo.api.dto.ingest.IngestBatchRequestDto;
import com.glea.nexo.api.dto.ingest.IngestBatchResponseDto;
import com.glea.nexo.application.ingest.IngestService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ingest/readings")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/batch")
    @Operation(summary = "Ingest telemetry readings in batch",
            description = "Each reading must include ts as the event time in ISO-8601 UTC. Requests with missing or invalid ts are rejected with a controlled 400 response.")
    public ResponseEntity<IngestBatchResponseDto> ingestBatch(@Valid @RequestBody IngestBatchRequestDto request) {
        return ResponseEntity.ok(ingestService.ingestBatch(request));
    }
}
